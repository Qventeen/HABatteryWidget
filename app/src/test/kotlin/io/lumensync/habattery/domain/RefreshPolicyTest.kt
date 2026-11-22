package io.lumensync.habattery.domain

import io.lumensync.habattery.domain.model.CacheState
import io.lumensync.habattery.domain.model.RefreshSettings
import io.lumensync.habattery.domain.model.Settings
import io.lumensync.habattery.testing.configuredSettings
import io.lumensync.habattery.testing.inverterReading
import io.lumensync.habattery.testing.reading
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RefreshPolicyTest {
  private fun s(auto: Boolean, interval: Int?, notify: Boolean = false) =
    Settings(refresh = RefreshSettings(auto, interval), notificationsOn = notify)

  @Test
  fun rules() {
    assertNull(RefreshPolicy.periodicIntervalMinutes(s(false, 30), hasWidgets = true))
    assertNull(RefreshPolicy.periodicIntervalMinutes(s(true, 30), hasWidgets = false))
    assertEquals(30, RefreshPolicy.periodicIntervalMinutes(s(true, 30), hasWidgets = true))
    assertEquals(
      30,
      RefreshPolicy.periodicIntervalMinutes(s(true, 30, notify = true), hasWidgets = false),
    )
    assertNull(RefreshPolicy.periodicIntervalMinutes(s(true, null), hasWidgets = true))
    assertNull(RefreshPolicy.periodicIntervalMinutes(s(true, 10), hasWidgets = true))
  }

  private val now = 10_000_000L

  private fun wanted(
    onUnlock: Boolean = true,
    hasWidgets: Boolean = true,
    settings: Settings = configuredSettings(),
    cache: CacheState = CacheState(reading = reading().copy(updatedAtMillis = now - 120_000L)),
  ) =
    RefreshPolicy.unlockSyncWanted(
      settings.copy(refresh = RefreshSettings(onUnlock = onUnlock)),
      cache,
      hasToken = true,
      hasWidgets = hasWidgets,
      nowMillis = now,
    )

  @Test fun unlock_base_isWanted() = assertTrue(wanted())

  @Test fun unlock_onUnlockOff() = assertFalse(wanted(onUnlock = false))

  @Test fun unlock_noWidgets() = assertFalse(wanted(hasWidgets = false))

  @Test
  fun unlock_nothingConfigured() =
    assertFalse(wanted(settings = configuredSettings(battery = false)))

  @Test
  fun unlock_refreshingSince10sAgo() =
    assertFalse(
      wanted(
        cache =
          CacheState(
            reading = reading().copy(updatedAtMillis = now - 120_000L),
            refreshingSinceMillis = now - 10_000L,
          )
      )
    )

  @Test
  fun unlock_batteryRead30sAgo() =
    assertFalse(
      wanted(cache = CacheState(reading = reading().copy(updatedAtMillis = now - 30_000L)))
    )

  /** The clock was set back: waiting until it catches up would keep unlock refresh off. */
  @Test
  fun unlock_readingDatedInTheFuture_isWanted() =
    assertTrue(
      wanted(cache = CacheState(reading = reading().copy(updatedAtMillis = now + 600_000L)))
    )

  @Test
  fun unlock_refreshingOlderThan2Min_doesNotBlock() =
    assertTrue(
      wanted(
        cache =
          CacheState(
            reading = reading().copy(updatedAtMillis = now - 120_000L),
            refreshingSinceMillis = now - 130_000L,
          )
      )
    )

  @Test
  fun unlock_inverterOnly_usesInverterReading() {
    val inverterOnly = configuredSettings(battery = false, inverter = true)
    val batteryOld = reading().copy(updatedAtMillis = now - 600_000L)
    fun inverterAge(ms: Long?) =
      wanted(
        settings = inverterOnly,
        cache =
          CacheState(
            reading = batteryOld,
            inverterReading = ms?.let { inverterReading().copy(updatedAtMillis = now - it) },
          ),
      )
    assertFalse(inverterAge(30_000L))
    assertTrue(inverterAge(600_000L))
    assertTrue(inverterAge(null))
  }

  @Test
  fun unlock_oneOldPartIsEnough() =
    assertTrue(
      wanted(
        settings = configuredSettings(inverter = true),
        cache =
          CacheState(
            reading = reading().copy(updatedAtMillis = now - 30_000L),
            inverterReading = inverterReading().copy(updatedAtMillis = now - 600_000L),
          ),
      )
    )
}
