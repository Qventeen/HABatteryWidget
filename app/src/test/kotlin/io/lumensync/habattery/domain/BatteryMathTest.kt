package io.lumensync.habattery.domain

import io.lumensync.habattery.domain.model.BatteryReading
import io.lumensync.habattery.domain.model.BatteryState
import io.lumensync.habattery.domain.model.CellStats
import io.lumensync.habattery.domain.model.Direction
import io.lumensync.habattery.domain.model.TimeRemaining
import kotlin.time.Duration.Companion.minutes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BatteryMathTest {
  private fun r(charge: Double, power: Double? = null, current: Double? = null) =
    BatteryReading(chargePercent = charge, powerW = power, currentA = current, updatedAtMillis = 0)

  @Test
  fun state_rules() {
    assertEquals(BatteryState.FULL, BatteryMath.state(100.0, 0.0))
    assertEquals(BatteryState.FULL, BatteryMath.state(99.5, -20.0))
    assertEquals(BatteryState.FULL, BatteryMath.state(100.0, null))
    assertEquals(BatteryState.IDLE, BatteryMath.state(99.4, 0.0))
    assertEquals(BatteryState.DISCHARGING, BatteryMath.state(100.0, -25.0))
    assertEquals(BatteryState.UNKNOWN, BatteryMath.state(80.0, null))
    assertEquals(BatteryState.IDLE, BatteryMath.state(50.0, 20.0))
    assertEquals(BatteryState.CHARGING, BatteryMath.state(50.0, 20.1))
    assertEquals(BatteryState.DISCHARGING, BatteryMath.state(50.0, -20.1))
  }

  @Test
  fun cellStats_rules() {
    assertEquals(CellStats(1, 2, 10), BatteryMath.cellStats(listOf(3.300, 3.310, 3.305)))
    assertEquals(CellStats(1, 3, 20), BatteryMath.cellStats(listOf(3.30, null, 3.32)))
    assertEquals(CellStats(1, 1, 0), BatteryMath.cellStats(listOf(3.3, 3.3)))
    assertNull(BatteryMath.cellStats(listOf(3.3)))
    assertNull(BatteryMath.cellStats(listOf(null, 3.3)))
    assertNull(BatteryMath.cellStats(emptyList()))
  }

  @Test
  fun timeRemaining_rules() {
    assertEquals(
      TimeRemaining(Direction.TO_EMPTY, 377.minutes),
      BatteryMath.timeRemaining(r(50.0, -500.0, -25.0), 314.0),
    )
    assertEquals(
      TimeRemaining(Direction.TO_EMPTY, 377.minutes), // current sign ignored
      BatteryMath.timeRemaining(r(50.0, -500.0, 25.0), 314.0),
    )
    assertEquals(
      TimeRemaining(Direction.TO_FULL, 94.minutes),
      BatteryMath.timeRemaining(r(80.0, 1000.0, 40.0), 314.0),
    )
    assertNull(BatteryMath.timeRemaining(r(50.0, -500.0, -0.4), 314.0))
    assertNull(BatteryMath.timeRemaining(r(50.0, -500.0, null), 314.0))
    assertNull(BatteryMath.timeRemaining(r(50.0, -500.0, -25.0), null))
    assertNull(BatteryMath.timeRemaining(r(50.0, 0.0, -25.0), 314.0)) // IDLE
    assertNull(BatteryMath.timeRemaining(r(50.0, null, -25.0), 314.0)) // UNKNOWN
    assertNull(BatteryMath.timeRemaining(r(100.0, 0.0, 5.0), 314.0)) // FULL
  }

  @Test
  fun timeRemaining_clampsCharge() {
    assertEquals(
      TimeRemaining(Direction.TO_EMPTY, 0.minutes),
      BatteryMath.timeRemaining(r(-2.0, -500.0, -25.0), 314.0),
    )
  }

  @Test
  fun progress_clamps() {
    assertEquals(1f, BatteryMath.progress(104.6))
    assertEquals(0f, BatteryMath.progress(-2.0))
    assertEquals(0.55f, BatteryMath.progress(55.0), 0.0001f)
  }
}
