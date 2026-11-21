package io.lumensync.habattery.domain

import io.lumensync.habattery.domain.model.CacheState
import io.lumensync.habattery.domain.model.LevelAlertState
import io.lumensync.habattery.domain.model.LevelName
import io.lumensync.habattery.domain.model.LevelValue
import io.lumensync.habattery.domain.model.Settings
import io.lumensync.habattery.domain.model.SyncError
import io.lumensync.habattery.domain.model.SyncResult
import io.lumensync.habattery.domain.model.TokenType
import io.lumensync.habattery.testing.FakeBatterySource
import io.lumensync.habattery.testing.FakeLevelNotifier
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import java.io.IOException
import org.junit.Test

class SyncBatteryTest {
  private val notifier = FakeLevelNotifier()
  private val widgets = FakeWidgetRefresher()
  private val socLevels = DefaultLevels.of(LevelValue.SOC, LevelBasis(null, null))

  private fun socId(name: LevelName) = socLevels.single { it.defaultName == name }.id

  private fun sync(
    source: FakeBatterySource,
    cache: FakeReadingCache = FakeReadingCache(),
    settings: Settings = configuredSettings(),
    token: String? = "t",
    userToken: String? = null,
    notifier: FakeLevelNotifier = this.notifier,
  ) = SyncBattery(
    FakeSettingsRepository(settings),
    FakeTokenStore(token, user = userToken),
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
  fun sendsActiveTypeToken() = runTest {
    val source = FakeBatterySource(SyncResult.Success(reading()))
    val user = configuredSettings(tokenType = TokenType.USER)

    sync(source, settings = user, token = "admin-t", userToken = "user-t")()

    assertEquals("user-t", source.lastToken)
  }

  @Test
  fun emptyActiveSlot_isNotConfigured() = runTest {
    val source = FakeBatterySource(SyncResult.Success(reading()))
    val user = configuredSettings(tokenType = TokenType.USER)

    val result = sync(source, settings = user, token = "admin-t")()

    assertEquals(SyncResult.Failure(SyncError.NotConfigured), result)
    assertEquals(0, source.calls)
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

  private val enteringLowCache
    get() =
      FakeReadingCache(
        CacheState(levelAlerts = mapOf("SOC" to LevelAlertState(current = socId(LevelName.GETTING_LOW))))
      )

  @Test
  fun enteringLow_postsOnce() = runTest {
    val cache = enteringLowCache
    val useCase =
      sync(
        FakeBatterySource(SyncResult.Success(reading(19.0))),
        cache,
        configuredSettings(notificationsOn = true),
      )

    useCase()
    assertEquals(1, notifier.posted.size)

    useCase()
    assertEquals(1, notifier.posted.size)
  }

  @Test
  fun noPermission_tracksOnly() = runTest {
    val notifier = FakeLevelNotifier(canPost = false)
    val cache = enteringLowCache

    sync(
      FakeBatterySource(SyncResult.Success(reading(19.0))),
      cache,
      configuredSettings(notificationsOn = true),
      notifier = notifier,
    )()

    assertEquals(emptyList<LevelAlert>(), notifier.posted)
    assertEquals(socId(LevelName.LOW), cache.current.levelAlerts["SOC"]?.current)
  }

  @Test
  fun notificationsOff_postsNothing() = runTest {
    val cache = enteringLowCache

    sync(FakeBatterySource(SyncResult.Success(reading(19.0))), cache)()

    assertEquals(emptyList<LevelAlert>(), notifier.posted)
    assertEquals(socId(LevelName.LOW), cache.current.levelAlerts["SOC"]?.current)
  }

  @Test
  fun failure_keepsAlertState() = runTest {
    val cache = enteringLowCache
    val before = cache.current.levelAlerts

    sync(
      FakeBatterySource(SyncResult.Failure(SyncError.Unreachable)),
      cache,
      configuredSettings(notificationsOn = true),
    )()

    assertEquals(before, cache.current.levelAlerts)
    assertEquals(emptyList<LevelAlert>(), notifier.posted)
  }

  @Test
  fun parallelRuns_postOnce() = runTest {
    val useCase =
      sync(
        FakeBatterySource(SyncResult.Success(reading(19.0))),
        enteringLowCache,
        configuredSettings(notificationsOn = true),
      )

    listOf(async { useCase() }, async { useCase() }).awaitAll()

    assertEquals(1, notifier.posted.size)
  }

  @Test
  fun sourceThrows_rethrowsButStillClearsRefreshingAndRedraws() = runTest {
    val source = FakeBatterySource(SyncResult.Success(reading())).apply { failWith = IOException("disk") }
    val cache = FakeReadingCache(CacheState(refreshingSinceMillis = 5L))
    val useCase = sync(source, cache, configuredSettings(notificationsOn = true))

    assertThrows(IOException::class.java) { runBlocking { useCase() } }

    assertNull(cache.current.refreshingSinceMillis)
    assertEquals(1, widgets.refreshes)
    assertEquals(emptyList<LevelAlert>(), notifier.posted)
  }
}
