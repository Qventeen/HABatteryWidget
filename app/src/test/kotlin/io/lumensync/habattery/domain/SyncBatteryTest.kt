package io.lumensync.habattery.domain

import io.lumensync.habattery.domain.model.CacheState
import io.lumensync.habattery.domain.model.Settings
import io.lumensync.habattery.domain.model.SyncError
import io.lumensync.habattery.domain.model.SyncResult
import io.lumensync.habattery.testing.FakeBatterySource
import io.lumensync.habattery.testing.FakeNotifier
import io.lumensync.habattery.testing.FakeReadingCache
import io.lumensync.habattery.testing.FakeSettingsRepository
import io.lumensync.habattery.testing.FakeTokenStore
import io.lumensync.habattery.testing.FakeWidgetRefresher
import io.lumensync.habattery.testing.configuredSettings
import io.lumensync.habattery.testing.reading
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import java.io.IOException
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncBatteryTest {
  private val notifier = FakeNotifier()
  private val widgets = FakeWidgetRefresher()

  private fun sync(
    source: FakeBatterySource,
    cache: FakeReadingCache = FakeReadingCache(),
    settings: Settings = configuredSettings(),
    token: String? = "t",
  ) = SyncBattery(
    FakeSettingsRepository(settings),
    FakeTokenStore(token),
    source,
    cache,
    notifier,
    widgets,
  )

  @Test
  fun notConfigured_savesErrorAndEndsNormally() = runTest {
    val source = FakeBatterySource(SyncResult.Success(reading()))
    val cache = FakeReadingCache(CacheState(refreshingSinceMillis = 5L))

    val result = sync(source, cache, token = null)()

    assertEquals(SyncResult.Failure(SyncError.NotConfigured), result)
    assertEquals(0, source.calls)
    assertEquals(SyncError.NotConfigured, cache.current.error)
    assertNull(cache.current.refreshingSinceMillis)
    assertEquals(1, widgets.refreshes)
  }

  @Test
  fun success_savesReadingAndClearsError() = runTest {
    val r = reading(60.0)
    val cache = FakeReadingCache(CacheState(error = SyncError.Unreachable, refreshingSinceMillis = 5L))

    val result = sync(FakeBatterySource(SyncResult.Success(r)), cache)()

    assertEquals(SyncResult.Success(r), result)
    assertEquals(r, cache.current.reading)
    assertNull(cache.current.error)
    assertNull(cache.current.refreshingSinceMillis)
    assertEquals(1, widgets.refreshes)
  }

  @Test
  fun failure_keepsLastReading() = runTest {
    val old = reading(40.0)
    val cache = FakeReadingCache(CacheState(reading = old, refreshingSinceMillis = 5L))

    sync(FakeBatterySource(SyncResult.Failure(SyncError.Unreachable)), cache)()

    assertEquals(old, cache.current.reading)
    assertEquals(SyncError.Unreachable, cache.current.error)
    assertNull(cache.current.refreshingSinceMillis)
    assertEquals(1, widgets.refreshes)
  }

  @Test
  fun full_notifiesOnce() = runTest {
    val full = SyncResult.Success(reading(100.0, 0.0))
    val cache = FakeReadingCache()
    val useCase = sync(FakeBatterySource(full), cache, configuredSettings(notifyOnFull = true))

    useCase()
    useCase()

    assertEquals(1, notifier.shown)
    assertTrue(cache.current.fullChargeNotified)
  }

  @Test
  fun belowRearm_rearms() = runTest {
    val cache = FakeReadingCache(CacheState(fullChargeNotified = true))
    val source = FakeBatterySource(SyncResult.Success(reading(98.0, -100.0)))

    sync(source, cache, configuredSettings(notifyOnFull = true))()

    assertFalse(cache.current.fullChargeNotified)
    assertEquals(0, notifier.shown)
  }

  @Test
  fun notifyDisabled_neverNotifies() = runTest {
    val cache = FakeReadingCache()

    sync(FakeBatterySource(SyncResult.Success(reading(100.0, 0.0))), cache)()

    assertEquals(0, notifier.shown)
    assertFalse(cache.current.fullChargeNotified)
  }

  @Test
  fun parallelRuns_notifyOnce() = runTest {
    val useCase =
      sync(
        FakeBatterySource(SyncResult.Success(reading(100.0, 0.0))),
        settings = configuredSettings(notifyOnFull = true),
      )

    listOf(async { useCase() }, async { useCase() }).awaitAll()

    assertEquals(1, notifier.shown)
  }

  @Test
  fun sourceThrows_rethrowsButStillClearsRefreshingAndRedraws() = runTest {
    val source = FakeBatterySource(SyncResult.Success(reading())).apply { failWith = IOException("disk") }
    val cache = FakeReadingCache(CacheState(refreshingSinceMillis = 5L))
    val useCase = sync(source, cache, configuredSettings(notifyOnFull = true))

    assertThrows(IOException::class.java) { runBlocking { useCase() } }

    assertNull(cache.current.refreshingSinceMillis)
    assertEquals(1, widgets.refreshes)
    assertEquals(0, notifier.shown)
  }
}
