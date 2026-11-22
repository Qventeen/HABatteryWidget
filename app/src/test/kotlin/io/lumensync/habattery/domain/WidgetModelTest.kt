package io.lumensync.habattery.domain

import io.lumensync.habattery.domain.WidgetModel.Data
import io.lumensync.habattery.domain.WidgetModel.Empty
import io.lumensync.habattery.domain.WidgetModel.EmptyKind
import io.lumensync.habattery.domain.WidgetModel.Flow
import io.lumensync.habattery.domain.WidgetModel.Temperatures
import io.lumensync.habattery.domain.model.BatteryReading
import io.lumensync.habattery.domain.model.CacheState
import io.lumensync.habattery.domain.model.ColorLevel
import io.lumensync.habattery.domain.model.CustomLevels
import io.lumensync.habattery.domain.model.DisplaySettings
import io.lumensync.habattery.domain.model.LevelColor
import io.lumensync.habattery.domain.model.LevelIcon
import io.lumensync.habattery.domain.model.LevelLook
import io.lumensync.habattery.domain.model.LevelSide
import io.lumensync.habattery.domain.model.LevelType
import io.lumensync.habattery.domain.model.LevelValue
import io.lumensync.habattery.domain.model.MainValue
import io.lumensync.habattery.domain.model.RefreshSettings
import io.lumensync.habattery.domain.model.Settings
import io.lumensync.habattery.domain.model.SyncError
import io.lumensync.habattery.domain.model.UnderSoc
import io.lumensync.habattery.domain.model.withCustomLevels
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

  private val plain = LevelLook(LevelColor.PLAIN)
  private val greenLeaf = LevelLook(LevelColor.GREEN, LevelIcon.LEAF)

  private fun settings(
    sensors: Int = 1,
    display: DisplaySettings = DisplaySettings(),
    refresh: RefreshSettings = RefreshSettings(),
    capacityAh: Double? = null,
  ): Settings {
    val base = configuredSettings()
    return base.copy(
      entities = base.entities.copy(temperatures = List(sensors) { "sensor.t$it" }),
      display = display,
      refresh = refresh,
      capacityAh = capacityAh,
    )
  }

  private fun custom(value: LevelValue, vararg levels: ColorLevel): Settings =
    settings().withCustomLevels(value, CustomLevels(on = true, levels = levels.toList()))

  /** Plain up to 51.99 V, green above it. */
  private val customVoltage =
    custom(
      LevelValue.VOLTAGE,
      ColorLevel(id = 1, side = LevelSide.BELOW, edge = 51.99, type = LevelType.NORMAL, color = LevelColor.PLAIN),
      ColorLevel(id = 2, type = LevelType.NORMAL),
    )

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
    fun flow(current: Double?) = data(sample.copy(currentA = current)).let { it.flow to it.mainLook.color }
    assertEquals(Flow.NONE to LevelColor.GREY, flow(0.1))
    assertEquals(Flow.NONE to LevelColor.GREY, flow(-0.1))
    assertEquals(Flow.UP to LevelColor.GREEN, flow(0.11))
    assertEquals(Flow.DOWN to LevelColor.AMBER, flow(-0.11))
    assertEquals(Flow.NONE to LevelColor.PLAIN, flow(null))
  }

  @Test
  fun `flow follows current when full`() {
    val full = data(sample.copy(chargePercent = 100.0, currentA = 0.5), configuredSettings().copy(capacityAh = 314.0))
    assertEquals(Flow.UP, full.flow)
    assertEquals(LevelLook(LevelColor.BLUE), full.mainLook)
    assertNull(full.timeLeft)
  }

  @Test
  fun main_idle_isGrey() {
    fun main(current: Double) = data(sample.copy(currentA = current), settings(capacityAh = 100.0)).mainLook
    assertEquals(LevelLook(LevelColor.GREY), main(0.1))
    assertEquals(LevelLook(LevelColor.BLUE), main(0.15))
  }

  @Test
  fun main_charge20A_100Ah_isLight() {
    fun main(current: Double) = data(sample.copy(currentA = current), settings(capacityAh = 100.0)).mainLook
    assertEquals(LevelLook(LevelColor.BLUE), main(20.0)) // a ↓ edge includes itself
    assertEquals(LevelLook(LevelColor.BLUE), main(19.95)) // shown as 20.0 A
    assertEquals(LevelLook(LevelColor.GREEN), main(20.1))
  }

  @Test
  fun main_discharge85A_100Ah_isRed() =
    assertEquals(LevelLook(LevelColor.RED), data(sample.copy(currentA = -85.0, totalCapacityAh = 100.0)).mainLook)

  @Test
  fun main_noCapacity() {
    assertEquals(LevelLook(LevelColor.GREEN), data(sample.copy(currentA = 85.0)).mainLook)
    assertEquals(LevelLook(LevelColor.AMBER), data(sample.copy(currentA = -85.0)).mainLook)
  }

  @Test
  fun main_powerDisplay_usesAmps() {
    val power = settings(display = DisplaySettings(mainValue = MainValue.POWER), capacityAh = 100.0)
    assertEquals(LevelLook(LevelColor.AMBER), data(sample.copy(currentA = 55.0, powerW = 1_457.5), power).mainLook)
  }

  @Test
  fun main_missingShownValue_isPlain() {
    val power = settings(display = DisplaySettings(mainValue = MainValue.POWER), capacityAh = 100.0)
    // The pill shows "—", so no level colour, although the current alone would give one.
    assertEquals(plain, data(sample.copy(currentA = -85.0, powerW = null), power).mainLook)
  }

  @Test
  fun main_missingCurrentWithPower_isGrey() {
    val power = settings(display = DisplaySettings(mainValue = MainValue.POWER))
    val d = data(sample.copy(currentA = null, powerW = -265.0), power)
    assertEquals(Flow.NONE, d.flow)
    assertEquals(LevelLook(LevelColor.GREY), d.mainLook)
  }

  @Test
  fun `time left from the overview`() = assertEquals(942.minutes, data().timeLeft)

  @Test
  fun `SOC rounding and ring`() {
    data().let {
      assertEquals(55, it.socPercent)
      assertEquals(0.546f, it.ringFraction, 1e-6f)
      assertEquals(LevelLook(LevelColor.GREEN), it.ringLook)
    }
    assertEquals(1f, data(sample.copy(chargePercent = 104.6)).ringFraction)
    assertEquals(0f, data(sample.copy(chargePercent = -3.0)).ringFraction)
  }

  @Test
  fun ring_followsSocLevels() {
    fun ring(soc: Double) = data(sample.copy(chargePercent = soc)).ringLook
    assertEquals(LevelLook(LevelColor.RED), ring(19.4)) // shown as 19
    assertEquals(LevelLook(LevelColor.RED), ring(19.6)) // shown as 20: a ↓ edge includes itself
    assertEquals(LevelLook(LevelColor.RED), ring(20.0))
    assertEquals(LevelLook(LevelColor.AMBER), ring(21.0))
    assertEquals(LevelLook(LevelColor.AMBER), ring(40.0))
    assertEquals(LevelLook(LevelColor.GREEN), ring(41.0))
    assertEquals(LevelLook(LevelColor.BLUE), ring(100.0))
  }

  @Test
  fun voltage_customLevels() {
    fun voltage(v: Double, settings: Settings = customVoltage) = data(sample.copy(totalVoltageV = v), settings).voltageLook
    assertEquals(LevelLook(LevelColor.GREEN), voltage(52.0))
    assertEquals(plain, voltage(51.99))
    assertEquals(LevelLook(LevelColor.GREEN), voltage(51.995)) // shown as 52.00 V
    // Custom levels off: the LiFePO4 defaults of the reading's 16 cells (52.0 V is Normal).
    assertEquals(LevelLook(LevelColor.GREEN), voltage(52.0, settings()))
  }

  @Test
  fun voltage_defaults_16cells() {
    fun looks(total: Double, average: Double?): Pair<LevelLook, LevelLook> =
      data(sample.copy(totalVoltageV = total, averageCellVoltageV = average)).let {
        it.voltageLook to it.averageCellLook
      }
    val green = LevelLook(LevelColor.GREEN)
    assertEquals(green to green, looks(52.8, 3.300))
    val blue = LevelLook(LevelColor.BLUE)
    assertEquals(blue to blue, looks(55.5, 3.469))
    val red = LevelLook(LevelColor.RED)
    assertEquals(red to red, looks(47.9, 2.994))
    // No average cell, no cell count: one plain level.
    assertEquals(plain to plain, looks(52.8, null))
  }

  @Test
  fun averageCell_followsVoltagePerCell() {
    fun cell(average: Double?) =
      data(sample.copy(totalVoltageV = 52.8, averageCellVoltageV = average), customVoltage).averageCellLook
    assertEquals(LevelLook(LevelColor.GREEN), cell(3.300)) // 16 cells: the 52.0 V edge is 3.25 V
    assertEquals(LevelLook(LevelColor.GREEN), cell(3.2495)) // shown as 3.250 V
    assertEquals(plain, cell(3.2494))
    assertEquals(plain, cell(null))
  }

  @Test
  fun averageCell_perCellEdgeIsRoundedLikeTheShownNumber() {
    fun levels(edge: Double) =
      custom(
        LevelValue.VOLTAGE,
        ColorLevel(id = 1, type = LevelType.NORMAL, color = LevelColor.PLAIN),
        ColorLevel(id = 2, side = LevelSide.ABOVE, edge = edge, type = LevelType.NORMAL),
      )
    fun cell(total: Double, average: Double, edge: Double) =
      data(sample.copy(totalVoltageV = total, averageCellVoltageV = average), levels(edge)).averageCellLook
    // 35.70 / 14 = 2.5500000000000003, yet the shown 2.550 V is on the edge.
    assertEquals(LevelLook(LevelColor.GREEN), cell(35.7, 2.55, 35.7))
    // 52.90 / 16 = 3.30625: the settings show 3.306 V, so the shown 3.306 V is on the edge.
    assertEquals(LevelLook(LevelColor.GREEN), cell(52.9, 3.306, 52.9))
  }

  @Test
  fun averageCell_withoutCellCount_isPlain() {
    val greenFirst =
      custom(
        LevelValue.VOLTAGE,
        ColorLevel(id = 1, type = LevelType.NORMAL),
        ColorLevel(id = 2, side = LevelSide.ABOVE, edge = 52.0, type = LevelType.WARNING),
      )
    assertEquals(plain, data(sample.copy(totalVoltageV = 0.0, averageCellVoltageV = 3.3), greenFirst).averageCellLook)
    assertEquals(plain, data(sample.copy(totalVoltageV = null), greenFirst).averageCellLook)
  }

  @Test
  fun averageCell_typedCountFillsAGap() =
    assertEquals(
      LevelLook(LevelColor.GREEN),
      data(sample.copy(totalVoltageV = null, averageCellVoltageV = 3.30), settings().copy(cellCount = 16))
        .averageCellLook,
    )

  @Test
  fun delta_9mV_blue_10mV_green() {
    fun delta(volts: Double) = data(sample.copy(deltaCellVoltageV = volts)).deltaLook
    assertEquals(LevelLook(LevelColor.BLUE), delta(0.009))
    assertEquals(LevelLook(LevelColor.GREEN), delta(0.010))
  }

  @Test
  fun icon_onlyForTemperatures() {
    val socWithIcon = custom(LevelValue.SOC, ColorLevel(id = 1, type = LevelType.NORMAL, icon = LevelIcon.LEAF))
    assertEquals(LevelLook(LevelColor.GREEN), data(settings = socWithIcon).ringLook)
  }

  private fun temps(vararg values: Double?, sensors: Int = values.size) =
    data(sample.copy(temperaturesC = values.toList()), settings(sensors = sensors)).temperatures

  @Test
  fun `no sensor configured shows no battery pill`() {
    assertEquals(Temperatures.None, data(settings = settings(sensors = 0)).temperatures)
  }

  @Test
  fun `battery pill shows the average of the sensors`() {
    assertEquals(Temperatures.Battery(25.0, greenLeaf, 1), data().temperatures)
    assertEquals(Temperatures.Battery(24.0, greenLeaf, 2), temps(25.0, 23.0))
    assertEquals(Temperatures.Battery(26.0, greenLeaf, 3), temps(25.0, 23.0, 30.0))
  }

  @Test
  fun `average is rounded for display after averaging`() {
    // 24.4 and 25.4 average 24.9: shown 25, not (24 + 25) / 2.
    val battery = temps(24.4, 25.4) as Temperatures.Battery
    assertEquals(24.9, battery.celsius!!, 1e-9)
  }

  @Test
  fun `missing temperature values are left out`() {
    assertEquals(Temperatures.Battery(30.0, greenLeaf, 3), temps(30.0, null, sensors = 3))
    assertEquals(Temperatures.Battery(null, plain, 2), temps(null, null))
    assertEquals(Temperatures.Battery(null, plain, 1), temps(sensors = 1))
  }

  @Test
  fun temperatures_worstSensor() {
    val blueSnowflake = LevelLook(LevelColor.BLUE, LevelIcon.SNOWFLAKE)
    assertEquals(Temperatures.Battery(14.0, blueSnowflake, 2), temps(25.0, 3.0))
    assertEquals(Temperatures.Battery(26.5, LevelLook(LevelColor.AMBER, LevelIcon.FLAME), 2), temps(3.0, 50.0))
    assertEquals(Temperatures.Battery(40.0, LevelLook(LevelColor.RED, LevelIcon.FLAME_ALERT), 2), temps(20.0, 60.0))
    assertEquals(Temperatures.Battery(9.5, LevelLook(LevelColor.RED, LevelIcon.SNOWFLAKE), 2), temps(-1.0, 20.0))
    assertEquals(Temperatures.Battery(null, plain, 2), temps(null, null))
  }

  @Test
  fun `temperature levels follow the shown whole degrees of each sensor`() {
    fun look(c: Double) = (temps(c) as Temperatures.Battery).look
    assertEquals(LevelLook(LevelColor.RED, LevelIcon.SNOWFLAKE), look(-0.6)) // shown as -1 °C
    assertEquals(LevelLook(LevelColor.RED, LevelIcon.SNOWFLAKE), look(-0.4)) // shown as 0 °C
    assertEquals(LevelLook(LevelColor.BLUE, LevelIcon.SNOWFLAKE), look(4.4)) // shown as 4 °C
    assertEquals(LevelLook(LevelColor.BLUE, LevelIcon.SNOWFLAKE), look(4.6)) // shown as 5 °C
    assertEquals(greenLeaf, look(5.6)) // shown as 6 °C
    assertEquals(greenLeaf, look(44.4)) // shown as 44 °C
    assertEquals(LevelLook(LevelColor.AMBER, LevelIcon.FLAME), look(44.6)) // shown as 45 °C
    assertEquals(LevelLook(LevelColor.AMBER, LevelIcon.FLAME), look(54.4)) // shown as 54 °C
    assertEquals(LevelLook(LevelColor.RED, LevelIcon.FLAME_ALERT), look(54.6)) // shown as 55 °C
    fun mosfet(c: Double) = data(sample.copy(mosfetTemperatureC = c)).mosfetLook
    assertEquals(greenLeaf, mosfet(64.4)) // shown as 64 °C
    assertEquals(LevelLook(LevelColor.AMBER, LevelIcon.FLAME), mosfet(64.6)) // shown as 65 °C
  }

  @Test
  fun mosfet_64_isGreenLeaf() = assertEquals(greenLeaf, data(sample.copy(mosfetTemperatureC = 64.0)).mosfetLook)

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
    assertEquals(plain, bare.voltageLook)
    assertEquals(plain, bare.mainLook)
    assertEquals(plain, bare.averageCellLook)
    assertEquals(plain, bare.deltaLook)
    assertEquals(plain, bare.mosfetLook)
  }

  @Test
  fun `cell and status values pass through`() {
    val d = data(sample.copy(deltaCellVoltageV = 0.018, mosfetTemperatureC = 85.0, balancing = true), error = SyncError.Unreachable)
    assertEquals(3.312, d.averageCellVoltage!!, 1e-9)
    assertEquals(18, d.deltaMv)
    assertEquals(LevelLook(LevelColor.GREEN), d.deltaLook)
    assertEquals(85.0, d.mosfet!!, 1e-9)
    assertEquals(LevelLook(LevelColor.RED, LevelIcon.FLAME_ALERT), d.mosfetLook)
    assertEquals(true, d.balancing)
    assertEquals(26.5, d.voltage!!, 1e-9)
    assertEquals(1_000L, d.updatedAtMillis)
    assertEquals(SyncError.Unreachable, d.error)
  }

  @Test
  fun `refreshing`() {
    val on = build(settings = settings(refresh = RefreshSettings(true, 30)), refreshingSince = 1_500L) as Data
    assertTrue(on.refreshing)
    val off = data()
    assertFalse(off.refreshing)
  }
}
