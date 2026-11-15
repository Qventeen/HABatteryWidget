package io.lumensync.habattery.domain

import io.lumensync.habattery.domain.model.BatteryReading
import io.lumensync.habattery.domain.model.CacheState
import io.lumensync.habattery.domain.model.Settings
import io.lumensync.habattery.domain.model.SyncError
import io.lumensync.habattery.testing.configuredSettings
import io.lumensync.habattery.testing.reading
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BatteryOverviewTest {
  private fun build(
    cache: CacheState = CacheState(reading = reading()),
    settings: Settings = configuredSettings(),
    hasToken: Boolean = true,
    now: Long = 2_000L,
  ) = BatteryOverview.build(cache, settings, hasToken, now)

  @Test
  fun setupNeeded_withoutToken_evenWithReading() {
    assertTrue(build(hasToken = false).setupNeeded)
    assertFalse(build(hasToken = true).setupNeeded)
  }

  @Test
  fun refreshing_window() {
    val cache = CacheState(reading = reading(), refreshingSinceMillis = 1_000L)
    assertTrue(build(cache, now = 1_000 + 119_999).refreshing)
    assertFalse(build(cache, now = 1_000 + 120_000).refreshing)
    assertFalse(build(cache, now = 500).refreshing) // clock moved back
    assertFalse(build(CacheState(reading = reading()), now = 1_000).refreshing)
  }

  @Test
  fun derivedValues_fromReading() {
    val r =
      BatteryReading(
        chargePercent = 60.0,
        powerW = -300.0,
        currentA = 6.0,
        updatedAtMillis = 1_000L,
      )
    val settings = configuredSettings().copy(capacityAh = 100.0)
    val overview = build(CacheState(reading = r), settings)
    assertEquals(r, overview.reading)
    assertEquals(BatteryMath.state(60.0, 6.0), overview.state)
    assertEquals(BatteryMath.timeRemaining(r, 100.0), overview.timeRemaining)
    assertEquals(settings.refresh, overview.refresh)
  }

  @Test
  fun noReading_noDerivedValues() {
    val overview = build(CacheState())
    assertNull(overview.reading)
    assertNull(overview.state)
    assertNull(overview.timeRemaining)
  }

  @Test
  fun noCapacity_noTime() {
    val r = BatteryReading(chargePercent = 60.0, powerW = -300.0, currentA = 6.0, updatedAtMillis = 1L)
    assertNull(build(CacheState(reading = r), configuredSettings()).timeRemaining)
  }

  @Test
  fun errorPassesThrough() {
    val cache = CacheState(reading = reading(), error = SyncError.Unreachable)
    assertEquals(SyncError.Unreachable, build(cache).error)
  }
}
