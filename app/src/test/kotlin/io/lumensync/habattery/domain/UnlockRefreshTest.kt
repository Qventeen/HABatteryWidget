package io.lumensync.habattery.domain

import io.lumensync.habattery.domain.model.CacheState
import io.lumensync.habattery.domain.model.RefreshSettings
import io.lumensync.habattery.testing.FakeReadingCache
import io.lumensync.habattery.testing.FakeSettingsRepository
import io.lumensync.habattery.testing.FakeSyncScheduler
import io.lumensync.habattery.testing.FakeTokenStore
import io.lumensync.habattery.testing.FakeWidgetRefresher
import io.lumensync.habattery.testing.configuredSettings
import io.lumensync.habattery.testing.reading
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UnlockRefreshTest {
  private var now = NOW
  private val cache =
    FakeReadingCache(CacheState(reading = reading().copy(updatedAtMillis = NOW - 120_000L)))
  private val widgets = FakeWidgetRefresher()
  private val scheduler = FakeSyncScheduler()

  private fun refresh(onUnlock: Boolean = true) =
    UnlockRefresh(
      settings =
        FakeSettingsRepository(
          configuredSettings().let { it.copy(refresh = RefreshSettings(onUnlock = onUnlock)) }
        ),
      cache = cache,
      tokens = FakeTokenStore(initial = "t", user = "t"),
      scheduler = scheduler,
      widgets = widgets,
      hasWidgets = { true },
      clock = { now },
    )

  @Test
  fun unlock_marksRefreshingAndQueuesOnlineSync() {
    assertTrue(runBlocking { refresh()() })
    assertEquals(NOW, cache.current.refreshingSinceMillis)
    assertEquals(1, widgets.refreshes)
    assertEquals(1, scheduler.syncWhenOnlineCalls)
    assertEquals(0, scheduler.syncNowCalls)
  }

  @Test
  fun switchOff_doesNothing() {
    assertFalse(runBlocking { refresh(onUnlock = false)() })
    assertNull(cache.current.refreshingSinceMillis)
    assertEquals(0, widgets.refreshes)
    assertEquals(0, scheduler.syncWhenOnlineCalls)
  }

  @Test
  fun secondUnlock_whileRefreshing_doesNothing() {
    val refresh = refresh()
    runBlocking {
      refresh()
      now = NOW + 5_000L
      assertFalse(refresh())
    }
    assertEquals(1, scheduler.syncWhenOnlineCalls)
    assertEquals(1, widgets.refreshes)
  }

  private companion object {
    const val NOW = 10_000_000L
  }
}
