package io.lumensync.habattery.domain

import io.lumensync.habattery.domain.WidgetModel.Data
import io.lumensync.habattery.domain.WidgetModel.Empty
import io.lumensync.habattery.domain.WidgetModel.EmptyKind
import io.lumensync.habattery.domain.WidgetModel.Flow
import io.lumensync.habattery.domain.WidgetModel.Temperatures
import io.lumensync.habattery.domain.model.BatteryReading
import io.lumensync.habattery.domain.model.CacheState
import io.lumensync.habattery.domain.model.DisplaySettings
import io.lumensync.habattery.domain.model.MainValue
import io.lumensync.habattery.domain.model.RefreshSettings
import io.lumensync.habattery.domain.model.Settings
import io.lumensync.habattery.domain.model.SyncError
import io.lumensync.habattery.domain.model.UnderSoc
import io.lumensync.habattery.testing.configuredSettings
import kotlin.time.Duration.Companion.minutes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetModelTest {
  private val sample =
    BatteryReading(
      chargePercent = 54.6,
      powerW = -265.0,
      currentA = -10.0,
      totalVoltageV = 26.5,
      capacityRemainingAh = 157.0,
      averageCellVoltageV = 3.312,
      deltaCellVoltageV = 0.012,
      mosfetTemperatureC = 27.0,
      temperaturesC = listOf(25.0),
      balancing = false,
      updatedAtMillis = 1_000L,
    )

  private fun settings(
    sensors: Int = 1,
    display: DisplaySettings = DisplaySettings(),
    refresh: RefreshSettings = RefreshSettings(),
  ): Settings {
    val base = configuredSettings()
    return base.copy(
      entities = base.entities.copy(temperatures = List(sensors) { "sensor.t$it" }),
      display = display,
      refresh = refresh,
    )
  }

  private fun build(
    reading: BatteryReading? = sample,
    settings: Settings = settings(),
    error: SyncError? = null,
    hasToken: Boolean = true,
    refreshingSince: Long? = null,
  ): WidgetModel =
    WidgetModel.build(
      BatteryOverview.build(CacheState(reading, error, refreshingSince), settings, hasToken, 2_000L),
      settings,
    )

  private fun data(
    reading: BatteryReading = sample,
    settings: Settings = settings(),
    error: SyncError? = null,
  ): Data = build(reading, settings, error) as Data

  @Test
  fun `setup needed`() = assertEquals(Empty(EmptyKind.SETUP, null), build(hasToken = false, error = SyncError.Unauthorized))

  @Test
  fun `no data keeps the error`() {
    assertEquals(Empty(EmptyKind.NO_DATA, SyncError.Unauthorized), build(reading = null, error = SyncError.Unauthorized))
    assertEquals(Empty(EmptyKind.NO_DATA, null), build(reading = null))
  }

  @Test
  fun `kWh and Ah under SOC`() {
    val kwh = data()
    assertEquals(UnderSoc.KWH, kwh.underSoc)
    assertEquals(4.1605, kwh.underSocValue!!, 1e-9)
    val ah = data(settings = settings(display = DisplaySettings(underSoc = UnderSoc.AH)))
    assertEquals(UnderSoc.AH, ah.underSoc)
    assertEquals(157.0, ah.underSocValue!!, 1e-9)
    assertNull(data(sample.copy(capacityRemainingAh = null)).underSocValue)
    assertNull(data(sample.copy(totalVoltageV = null)).underSocValue)
  }

  @Test
  fun `current or power without sign`() {
    val current = data()
    assertEquals(MainValue.CURRENT, current.main)
    assertEquals(10.0, current.mainValue!!, 1e-9)
    val power = data(settings = settings(display = DisplaySettings(mainValue = MainValue.POWER)))
    assertEquals(MainValue.POWER, power.main)
    assertEquals(265.0, power.mainValue!!, 1e-9)
    assertNull(data(sample.copy(powerW = null), settings(display = DisplaySettings(mainValue = MainValue.POWER))).mainValue)
  }

  @Test
  fun `flow at the 0_1 A edges`() {
    fun flow(current: Double?) = data(sample.copy(currentA = current)).let { it.flow to it.mainLevel }
    assertEquals(Flow.NONE to Level.IDLE, flow(0.1))
    assertEquals(Flow.NONE to Level.IDLE, flow(-0.1))
    assertEquals(Flow.UP to Level.OK, flow(0.11))
    assertEquals(Flow.DOWN to Level.WARN, flow(-0.11))
    assertEquals(Flow.NONE to Level.IDLE, flow(null))
  }

  @Test
  fun `flow follows current when full`() {
    val full = data(sample.copy(chargePercent = 100.0, currentA = 0.5), configuredSettings().copy(capacityAh = 314.0))
    assertEquals(Flow.UP, full.flow)
    assertEquals(Level.OK, full.mainLevel)
    assertNull(full.timeLeft)
  }

  @Test
  fun `time left from the overview`() = assertEquals(942.minutes, data().timeLeft)

  @Test
  fun `SOC rounding and ring`() {
    data().let {
      assertEquals(55, it.socPercent)
      assertEquals(0.546f, it.ringFraction, 1e-6f)
      assertEquals(Level.OK, it.ringLevel)
    }
    assertEquals(Level.WARN, data(sample.copy(chargePercent = 24.4)).ringLevel)
    assertEquals(Level.BAD, data(sample.copy(chargePercent = 14.4)).ringLevel)
    assertEquals(1f, data(sample.copy(chargePercent = 104.6)).ringFraction)
    assertEquals(0f, data(sample.copy(chargePercent = -3.0)).ringFraction)
  }

  @Test
  fun `temperature pills by configured sensors`() {
    assertEquals(Temperatures.None, data(settings = settings(sensors = 0)).temperatures)
    assertEquals(Temperatures.One(25.0, Level.PLAIN), data().temperatures)
    assertEquals(
      Temperatures.MaxMin(47.0, Level.WARN, 44.0, Level.PLAIN),
      data(sample.copy(temperaturesC = listOf(44.0, 47.0)), settings(sensors = 2)).temperatures,
    )
  }

  @Test
  fun `missing temperature values`() {
    assertEquals(
      Temperatures.MaxMin(null, Level.PLAIN, null, Level.PLAIN),
      data(sample.copy(temperaturesC = listOf(null, null)), settings(sensors = 2)).temperatures,
    )
    assertEquals(
      Temperatures.One(null, Level.PLAIN),
      data(sample.copy(temperaturesC = emptyList()), settings(sensors = 1)).temperatures,
    )
    assertEquals(
      Temperatures.MaxMin(30.0, Level.PLAIN, 30.0, Level.PLAIN),
      data(sample.copy(temperaturesC = listOf(30.0, null)), settings(sensors = 3)).temperatures,
    )
  }

  @Test
  fun `temperature levels follow the shown whole degrees`() {
    fun one(c: Double) = data(sample.copy(temperaturesC = listOf(c))).temperatures
    assertEquals(Temperatures.One(0.4, Level.BAD), one(0.4)) // shown as 0 °C
    assertEquals(Temperatures.One(45.4, Level.PLAIN), one(45.4)) // shown as 45 °C
    assertEquals(Temperatures.One(55.4, Level.WARN), one(55.4)) // shown as 55 °C
    assertEquals(Level.PLAIN, data(sample.copy(mosfetTemperatureC = 65.4)).mosfetLevel)
  }

  @Test
  fun `null inputs give null values`() {
    val bare = data(BatteryReading(chargePercent = 50.0, updatedAtMillis = 1L))
    assertNull(bare.voltage)
    assertNull(bare.mainValue)
    assertNull(bare.averageCellVoltage)
    assertNull(bare.deltaMv)
    assertNull(bare.mosfet)
    assertNull(bare.balancing)
    assertNull(bare.timeLeft)
    assertEquals(Level.PLAIN, bare.deltaLevel)
    assertEquals(Level.PLAIN, bare.mosfetLevel)
  }

  @Test
  fun `cell and status values pass through`() {
    val d = data(sample.copy(deltaCellVoltageV = 0.018, mosfetTemperatureC = 85.0, balancing = true), error = SyncError.Unreachable)
    assertEquals(3.312, d.averageCellVoltage!!, 1e-9)
    assertEquals(18, d.deltaMv)
    assertEquals(Level.WARN, d.deltaLevel)
    assertEquals(85.0, d.mosfet!!, 1e-9)
    assertEquals(Level.BAD, d.mosfetLevel)
    assertEquals(true, d.balancing)
    assertEquals(26.5, d.voltage!!, 1e-9)
    assertEquals(1_000L, d.updatedAtMillis)
    assertEquals(SyncError.Unreachable, d.error)
  }

  @Test
  fun `refreshing and auto refresh`() {
    val on = build(settings = settings(refresh = RefreshSettings(true, 30)), refreshingSince = 1_500L) as Data
    assertTrue(on.refreshing)
    assertTrue(on.autoRefresh)
    val off = data()
    assertFalse(off.refreshing)
    assertFalse(off.autoRefresh)
  }
}
