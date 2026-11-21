package io.lumensync.habattery.domain

import androidx.work.ListenableWorker
import io.lumensync.habattery.domain.model.CacheState
import io.lumensync.habattery.domain.model.FetchRequest
import io.lumensync.habattery.domain.model.FetchResult
import io.lumensync.habattery.domain.model.LevelAlertState
import io.lumensync.habattery.domain.model.LevelName
import io.lumensync.habattery.domain.model.LevelValue
import io.lumensync.habattery.domain.model.PartResult
import io.lumensync.habattery.domain.model.Settings
import io.lumensync.habattery.domain.model.SyncError
import io.lumensync.habattery.domain.model.TokenType
import io.lumensync.habattery.system.toWorkResult
import io.lumensync.habattery.testing.FakeBatterySource
import io.lumensync.habattery.testing.FakeLevelNotifier
import io.lumensync.habattery.testing.FakeReadingCache
import io.lumensync.habattery.testing.FakeSettingsRepository
import io.lumensync.habattery.testing.FakeTokenStore
import io.lumensync.habattery.testing.FakeWidgetRefresher
import io.lumensync.habattery.testing.batteryOk
import io.lumensync.habattery.testing.configuredSettings
import io.lumensync.habattery.testing.inverterReading
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

  private val notConfigured = PartResult.Failed(SyncError.NotConfigured)
  private val bothOk = FetchResult(PartResult.Ok(reading()), PartResult.Ok(inverterReading()))
  private val badResponse = PartResult.Failed(SyncError.BadResponse)

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
  fun noToken_bothNotConfigured_noRequest() = runTest {
    val source = FakeBatterySource(bothOk)
    val cache = FakeReadingCache(CacheState(refreshingSinceMillis = 5L))

    val result = sync(source, cache, configuredSettings(inverter = true), token = null)()

    assertEquals(FetchResult(notConfigured, notConfigured), result)
    assertEquals(0, source.calls)
    assertEquals(SyncError.NotConfigured, cache.current.error)
    assertEquals(SyncError.NotConfigured, cache.current.inverterError)
    assertNull(cache.current.refreshingSinceMillis)
    assertEquals(1, widgets.refreshes)
  }

  @Test
  fun noUrl_bothNotConfigured_noRequest() = runTest {
    val source = FakeBatterySource(bothOk)
    val settings = configuredSettings(inverter = true)
    val noUrl = settings.copy(connection = settings.connection.copy(url = ""))

    val result = sync(source, settings = noUrl)()

    assertEquals(FetchResult(notConfigured, notConfigured), result)
    assertEquals(0, source.calls)
  }

  @Test
  fun noPartConfigured_noRequest() = runTest {
    val source = FakeBatterySource(batteryOk(reading()))
    val cache = FakeReadingCache()

    val result = sync(source, cache, configuredSettings(battery = false))()

    assertEquals(FetchResult(notConfigured, notConfigured), result)
    assertEquals(0, source.calls)
    assertEquals(SyncError.NotConfigured, cache.current.error)
    assertEquals(SyncError.NotConfigured, cache.current.inverterError)
  }

  @Test
  fun bothConfigured_bothSaved() = runTest {
    val r = reading(60.0)
    val inverter = inverterReading()
    val settings = configuredSettings(inverter = true)
    val source = FakeBatterySource(FetchResult(PartResult.Ok(r), PartResult.Ok(inverter)))
    val unreachable = SyncError.Unreachable
    val cache = FakeReadingCache(CacheState(error = unreachable, inverterError = unreachable))

    val result = sync(source, cache, settings)()

    assertEquals(FetchRequest(settings.entities, settings.inverter.entities), source.lastRequest)
    assertEquals(FetchResult(PartResult.Ok(r), PartResult.Ok(inverter)), result)
    assertEquals(r, cache.current.reading)
    assertNull(cache.current.error)
    assertEquals(inverter, cache.current.inverterReading)
    assertNull(cache.current.inverterError)
  }

  @Test
  fun batteryFails_inverterStillSaved() = runTest {
    val old = reading(40.0)
    val inverter = inverterReading()
    val cache = FakeReadingCache(CacheState(reading = old))

    sync(
      FakeBatterySource(FetchResult(badResponse, PartResult.Ok(inverter))),
      cache,
      configuredSettings(inverter = true),
    )()

    assertEquals(SyncError.BadResponse, cache.current.error)
    assertEquals(old, cache.current.reading)
    assertEquals(inverter, cache.current.inverterReading)
    assertNull(cache.current.inverterError)
  }

  @Test
  fun inverterFails_batteryUnchangedAndSaved() = runTest {
    val cache =
      FakeReadingCache(CacheState(error = SyncError.BadResponse, levelAlerts = enteringLow))

    sync(
      FakeBatterySource(FetchResult(PartResult.Ok(reading(19.0)), badResponse)),
      cache,
      configuredSettings(notificationsOn = true, inverter = true),
    )()

    assertEquals(reading(19.0), cache.current.reading)
    assertNull(cache.current.error)
    assertEquals(SyncError.BadResponse, cache.current.inverterError)
    assertEquals(1, notifier.posted.size)
    assertEquals(socId(LevelName.LOW), cache.current.levelAlerts["SOC"]?.current)
  }

  @Test
  fun onlyInverterConfigured_fetchesInverterOnly() = runTest {
    val old = reading(40.0)
    val inverter = inverterReading()
    val settings = configuredSettings(battery = false, inverter = true)
    val source = FakeBatterySource(FetchResult(null, PartResult.Ok(inverter)))
    val cache = FakeReadingCache(CacheState(reading = old))

    val result = sync(source, cache, settings)()

    assertEquals(FetchRequest(null, settings.inverter.entities), source.lastRequest)
    assertEquals(FetchResult(notConfigured, PartResult.Ok(inverter)), result)
    assertEquals(SyncError.NotConfigured, cache.current.error)
    assertEquals(old, cache.current.reading)
    assertEquals(inverter, cache.current.inverterReading)
    assertEquals(ListenableWorker.Result.success(), result.toWorkResult(runAttemptCount = 0))
  }

  @Test
  fun askedPartMissing_isBadResponse() = runTest {
    val r = reading()
    val both = configuredSettings(inverter = true)

    val result = sync(FakeBatterySource(batteryOk(r)), settings = both)()

    assertEquals(FetchResult(PartResult.Ok(r), badResponse), result)
  }

  @Test
  fun sendsActiveTypeToken() = runTest {
    val source = FakeBatterySource(batteryOk(reading()))
    val user = configuredSettings(tokenType = TokenType.USER)

    sync(source, settings = user, token = "admin-t", userToken = "user-t")()

    assertEquals("user-t", source.lastToken)
  }

  @Test
  fun asksBatteryPartOnly() = runTest {
    val source = FakeBatterySource(batteryOk(reading()))
    val settings = configuredSettings()

    sync(source, settings = settings)()

    assertEquals(FetchRequest(settings.entities, inverter = null), source.lastRequest)
  }

  @Test
  fun emptyActiveSlot_isNotConfigured() = runTest {
    val source = FakeBatterySource(batteryOk(reading()))
    val user = configuredSettings(tokenType = TokenType.USER)

    val result = sync(source, settings = user, token = "admin-t")()

    assertEquals(FetchResult(notConfigured, notConfigured), result)
    assertEquals(0, source.calls)
  }

  @Test
  fun success_savesReadingAndClearsError() = runTest {
    val r = reading(60.0)
    val cache = FakeReadingCache(CacheState(error = SyncError.Unreachable, refreshingSinceMillis = 5L))

    val result = sync(FakeBatterySource(batteryOk(r)), cache)()

    assertEquals(PartResult.Ok(r), result.battery)
    assertEquals(r, cache.current.reading)
    assertNull(cache.current.error)
    assertNull(cache.current.refreshingSinceMillis)
    assertEquals(1, widgets.refreshes)
  }

  @Test
  fun failedPart_keepsLastReading() = runTest {
    val old = reading(40.0)
    val oldInverter = inverterReading()
    val cache =
      FakeReadingCache(
        CacheState(reading = old, inverterReading = oldInverter, refreshingSinceMillis = 5L)
      )
    val unreachable = PartResult.Failed(SyncError.Unreachable)

    val both = configuredSettings(inverter = true)

    sync(FakeBatterySource(FetchResult(unreachable, unreachable)), cache, both)()

    assertEquals(old, cache.current.reading)
    assertEquals(SyncError.Unreachable, cache.current.error)
    assertEquals(oldInverter, cache.current.inverterReading)
    assertEquals(SyncError.Unreachable, cache.current.inverterError)
    assertNull(cache.current.refreshingSinceMillis)
    assertEquals(1, widgets.refreshes)
  }

  private val enteringLow
    get() = mapOf("SOC" to LevelAlertState(current = socId(LevelName.GETTING_LOW)))

  private val enteringLowCache
    get() = FakeReadingCache(CacheState(levelAlerts = enteringLow))

  @Test
  fun enteringLow_postsOnce() = runTest {
    val cache = enteringLowCache
    val useCase =
      sync(
        FakeBatterySource(batteryOk(reading(19.0))),
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
      FakeBatterySource(batteryOk(reading(19.0))),
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

    sync(FakeBatterySource(batteryOk(reading(19.0))), cache)()

    assertEquals(emptyList<LevelAlert>(), notifier.posted)
    assertEquals(socId(LevelName.LOW), cache.current.levelAlerts["SOC"]?.current)
  }

  @Test
  fun failure_keepsAlertState() = runTest {
    val cache = enteringLowCache
    val before = cache.current.levelAlerts

    sync(
      FakeBatterySource(FetchResult(PartResult.Failed(SyncError.Unreachable), null)),
      cache,
      configuredSettings(notificationsOn = true),
    )()

    assertEquals(before, cache.current.levelAlerts)
    assertEquals(emptyList<LevelAlert>(), notifier.posted)
  }

  private fun defaultId(value: LevelValue, name: LevelName) =
    DefaultLevels.of(value, LevelBasis(null, null)).single { it.defaultName == name }.id

  private fun gridId(name: LevelName) = defaultId(LevelValue.GRID_VOLTAGE, name)

  private val gridNormal
    get() = mapOf("GRID_VOLTAGE" to LevelAlertState(current = gridId(LevelName.NORMAL)))

  private val gridLow = inverterReading().copy(gridVoltageV = 205.0)

  @Test
  fun bothParts_postLevelAlerts_andKeepBothStates() = runTest {
    val cache = FakeReadingCache(CacheState(levelAlerts = enteringLow + gridNormal))

    sync(
      FakeBatterySource(FetchResult(PartResult.Ok(reading(19.0)), PartResult.Ok(gridLow))),
      cache,
      configuredSettings(notificationsOn = true, inverter = true),
    )()

    assertEquals(
      listOf(LevelValue.SOC to LevelName.LOW, LevelValue.GRID_VOLTAGE to LevelName.LOW),
      notifier.posted.map { it.value to it.level.defaultName },
    )
    val stored = cache.current.levelAlerts
    assertEquals(socId(LevelName.LOW), stored["SOC"]?.current)
    assertEquals(
      LevelAlertState(gridId(LevelName.LOW), listOf(gridId(LevelName.LOW))),
      stored["GRID_VOLTAGE"],
    )
    assertEquals(
      LevelAlertState(defaultId(LevelValue.OUTPUT_VOLTAGE, LevelName.NORMAL)),
      stored["OUTPUT_VOLTAGE"],
    )
  }

  @Test
  fun batteryFails_inverterLevelStillPosts() = runTest {
    val cache = FakeReadingCache(CacheState(levelAlerts = enteringLow + gridNormal))

    sync(
      FakeBatterySource(FetchResult(badResponse, PartResult.Ok(gridLow))),
      cache,
      configuredSettings(notificationsOn = true, inverter = true),
    )()

    assertEquals(listOf(LevelValue.GRID_VOLTAGE), notifier.posted.map { it.value })
    assertEquals(enteringLow["SOC"], cache.current.levelAlerts["SOC"])
    assertEquals(gridId(LevelName.LOW), cache.current.levelAlerts["GRID_VOLTAGE"]?.current)
  }

  @Test
  fun parallelRuns_postOnce() = runTest {
    val useCase =
      sync(
        FakeBatterySource(batteryOk(reading(19.0))),
        enteringLowCache,
        configuredSettings(notificationsOn = true),
      )

    listOf(async { useCase() }, async { useCase() }).awaitAll()

    assertEquals(1, notifier.posted.size)
  }

  @Test
  fun sourceThrows_rethrowsButStillClearsRefreshingAndRedraws() = runTest {
    val source = FakeBatterySource(batteryOk(reading())).apply { failWith = IOException("disk") }
    val cache = FakeReadingCache(CacheState(refreshingSinceMillis = 5L))
    val useCase = sync(source, cache, configuredSettings(notificationsOn = true))

    assertThrows(IOException::class.java) { runBlocking { useCase() } }

    assertNull(cache.current.refreshingSinceMillis)
    assertEquals(1, widgets.refreshes)
    assertEquals(emptyList<LevelAlert>(), notifier.posted)
  }
}
