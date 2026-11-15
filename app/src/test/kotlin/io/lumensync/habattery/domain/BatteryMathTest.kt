package io.lumensync.habattery.domain

import io.lumensync.habattery.domain.model.BatteryReading
import io.lumensync.habattery.domain.model.BatteryState
import io.lumensync.habattery.domain.model.Direction
import io.lumensync.habattery.domain.model.TimeRemaining
import kotlin.time.Duration.Companion.minutes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BatteryMathTest {
  private fun reading(soc: Double, currentA: Double?, ahLeft: Double?) =
    BatteryReading(
      chargePercent = soc,
      currentA = currentA,
      capacityRemainingAh = ahLeft,
      updatedAtMillis = 0,
    )

  private val base = reading(50.0, null, null)

  @Test
  fun `idle inside the 0_1 A band`() {
    assertEquals(BatteryState.IDLE, BatteryMath.state(50.0, 0.1))
    assertEquals(BatteryState.IDLE, BatteryMath.state(50.0, -0.1))
    assertEquals(BatteryState.IDLE, BatteryMath.state(50.0, 0.0))
  }

  @Test
  fun `charging and discharging outside the band`() {
    assertEquals(BatteryState.CHARGING, BatteryMath.state(50.0, 0.11))
    assertEquals(BatteryState.DISCHARGING, BatteryMath.state(50.0, -0.11))
  }

  @Test
  fun `unknown without current`() = assertEquals(BatteryState.UNKNOWN, BatteryMath.state(50.0, null))

  @Test
  fun `full at 100 percent unless discharging`() {
    assertEquals(BatteryState.FULL, BatteryMath.state(99.6, null))
    assertEquals(BatteryState.FULL, BatteryMath.state(100.0, -0.1))
    assertEquals(BatteryState.FULL, BatteryMath.state(100.0, 5.0))
    assertEquals(BatteryState.DISCHARGING, BatteryMath.state(100.0, -0.2))
  }

  @Test
  fun `to empty uses Ah left`() =
    assertEquals(
      TimeRemaining(Direction.TO_EMPTY, 942.minutes),
      BatteryMath.timeRemaining(reading(50.0, -10.0, 157.0), null),
    )

  @Test
  fun `to empty falls back to SOC and capacity`() =
    assertEquals(
      942.minutes,
      BatteryMath.timeRemaining(reading(50.0, -10.0, null), 314.0)?.duration,
    )

  @Test
  fun `to empty needs Ah left or capacity`() =
    assertNull(BatteryMath.timeRemaining(reading(50.0, -10.0, null), null))

  @Test
  fun `to full uses capacity minus Ah left`() =
    assertEquals(
      TimeRemaining(Direction.TO_FULL, 942.minutes),
      BatteryMath.timeRemaining(reading(50.0, 10.0, 157.0), 314.0),
    )

  @Test
  fun `to full clamps at zero`() =
    assertEquals(
      0.minutes,
      BatteryMath.timeRemaining(reading(99.0, 10.0, 320.0), 314.0)?.duration,
    )

  @Test
  fun `to full falls back to SOC`() =
    assertEquals(
      942.minutes,
      BatteryMath.timeRemaining(reading(50.0, 10.0, null), 314.0)?.duration,
    )

  @Test
  fun `to full needs capacity`() =
    assertNull(BatteryMath.timeRemaining(reading(50.0, 10.0, 157.0), null))

  @Test
  fun `no time below 0_5 A`() =
    assertNull(BatteryMath.timeRemaining(reading(50.0, -0.4, 157.0), 314.0))

  @Test
  fun `no time when idle`() =
    assertNull(BatteryMath.timeRemaining(reading(50.0, 0.05, 157.0), 314.0))

  @Test
  fun `no time without current or when full`() {
    assertNull(BatteryMath.timeRemaining(reading(50.0, null, 157.0), 314.0))
    assertNull(BatteryMath.timeRemaining(reading(100.0, 5.0, 157.0), 314.0))
  }

  @Test
  fun `delta in mV rounds`() {
    assertEquals(13, BatteryMath.deltaMv(base.copy(deltaCellVoltageV = 0.0125)))
    assertEquals(40, BatteryMath.deltaMv(base.copy(deltaCellVoltageV = 0.040)))
    assertNull(BatteryMath.deltaMv(base.copy(deltaCellVoltageV = null)))
  }

  @Test
  fun progress_clamps() {
    assertEquals(1f, BatteryMath.progress(104.6))
    assertEquals(0f, BatteryMath.progress(-2.0))
    assertEquals(0.55f, BatteryMath.progress(55.0), 0.0001f)
  }
}
