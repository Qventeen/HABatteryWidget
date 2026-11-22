package io.lumensync.habattery.domain

import io.lumensync.habattery.domain.model.CacheState
import io.lumensync.habattery.domain.model.RefreshSettings
import io.lumensync.habattery.testing.configuredSettings
import io.lumensync.habattery.testing.inverterReading
import io.lumensync.habattery.testing.reading
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FreshnessTest {
  private val min = 60_000L

  @Test
  fun lateAfter_autoOn_isIntervalPlus5() =
    assertEquals(35 * min, Freshness.lateAfterMillis(RefreshSettings(true, 30)))

  @Test
  fun lateAfter_autoOff_is20() =
    assertEquals(20 * min, Freshness.lateAfterMillis(RefreshSettings(false, 60)))

  @Test
  fun invalidInterval_uses15() {
    assertEquals(20 * min, Freshness.lateAfterMillis(RefreshSettings(true, null)))
    assertEquals(20 * min, Freshness.lateAfterMillis(RefreshSettings(true, 5)))
  }

  @Test
  fun isLate_atTheEdge() {
    val off = RefreshSettings()
    assertFalse(Freshness.isLate(0L, 20 * min, off))
    assertTrue(Freshness.isLate(0L, 20 * min + 1, off))
  }

  @Test
  fun futureReading_isFreshAndZeroOld() {
    assertFalse(Freshness.isLate(NOW + min, NOW, RefreshSettings()))
    assertEquals(0L, Freshness.ageMinutes(NOW + min, NOW))
  }

  @Test
  fun ageMinutes_roundsDown() = assertEquals(3L, Freshness.ageMinutes(NOW - 239_000L, NOW))

  private val t = 1_000_000L

  private fun cache() =
    CacheState(
      reading = reading().copy(updatedAtMillis = t),
      inverterReading = inverterReading().copy(updatedAtMillis = t + 2 * min),
    )

  private fun nextLate(now: Long, battery: Boolean = true, inverter: Boolean = true) =
    Freshness.nextLateAt(
      cache(),
      configuredSettings(battery = battery, inverter = inverter),
      hasToken = true,
      nowMillis = now,
    )

  @Test
  fun nextLateAt_earliestGreenPart() {
    assertEquals(t + 20 * min, nextLate(t + min))
    assertEquals(t + 22 * min, nextLate(t + 21 * min))
    assertNull(nextLate(t + 23 * min))
  }

  @Test
  fun nextLateAt_nothingConfigured_isNull() {
    assertNull(nextLate(t + min, battery = false, inverter = false))
  }

  @Test
  fun nextLateAt_skipsPartNotConfigured() {
    assertEquals(t + 22 * min, nextLate(t + min, battery = false))
  }

  @Test
  fun refreshing_within2Minutes() {
    assertTrue(Freshness.isRefreshing(NOW - 119_999L, NOW))
    assertFalse(Freshness.isRefreshing(NOW - 120_000L, NOW))
    assertFalse(Freshness.isRefreshing(null, NOW))
  }

  private companion object {
    const val NOW = 10_000_000L
  }
}
