package io.lumensync.habattery.domain

import io.lumensync.habattery.domain.model.RefreshSettings
import io.lumensync.habattery.testing.FakeSettingsRepository
import io.lumensync.habattery.testing.FakeSyncScheduler
import io.lumensync.habattery.testing.FakeWidgetRefresher
import io.lumensync.habattery.testing.configuredSettings
import io.lumensync.habattery.domain.port.WidgetRefresher
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ToggleAutoRefreshTest {
  @Test fun `on with no interval uses 15`() = assertEquals(RefreshSettings(true, 15), RefreshSettings(false, null).toggled())

  @Test fun `on keeps a valid interval`() = assertEquals(RefreshSettings(true, 30), RefreshSettings(false, 30).toggled())

  @Test
  fun `on fixes an out-of-range interval`() {
    assertEquals(15, RefreshSettings(false, 5).toggled().intervalMinutes)
    assertEquals(1440, RefreshSettings(false, 2000).toggled().intervalMinutes)
  }

  @Test fun `off keeps the interval`() = assertEquals(RefreshSettings(false, 30), RefreshSettings(true, 30).toggled())

  @Test
  fun `turning on saves, applies, syncs and redraws`() = runTest {
    val settings = FakeSettingsRepository(configuredSettings().copy(refresh = RefreshSettings(false, null)))
    val scheduler = FakeSyncScheduler()
    val widgets = FakeWidgetRefresher()

    ToggleAutoRefresh(settings, scheduler, widgets)()

    val stored = settings.settings.value
    assertEquals(RefreshSettings(true, 15), stored.refresh)
    assertEquals(listOf(stored to true), scheduler.applied)
    assertEquals(1, scheduler.syncNowCalls)
    assertTrue(widgets.refreshes >= 1)
  }

  @Test
  fun `a double tap ends with the schedule matching the stored setting`() = runTest {
    val settings = FakeSettingsRepository(configuredSettings().copy(refresh = RefreshSettings(false, 30)))
    val scheduler = FakeSyncScheduler()
    var redraws = 0
    // The first redraw is slow, so the second tap overtakes the first one without serializing.
    val slowFirst =
      object : WidgetRefresher {
        override suspend fun refreshAll() {
          if (redraws++ == 0) repeat(3) { yield() }
        }
      }
    val toggle = ToggleAutoRefresh(settings, scheduler, slowFirst)

    coroutineScope {
      launch { toggle() }
      launch { toggle() }
    }

    assertEquals(RefreshSettings(false, 30), settings.settings.value.refresh)
    assertEquals(settings.settings.value.refresh, scheduler.applied.last().first.refresh)
  }

  @Test
  fun `turning off applies without a sync`() = runTest {
    val settings = FakeSettingsRepository(configuredSettings().copy(refresh = RefreshSettings(true, 30)))
    val scheduler = FakeSyncScheduler()
    val widgets = FakeWidgetRefresher()

    ToggleAutoRefresh(settings, scheduler, widgets)()

    val stored = settings.settings.value
    assertEquals(RefreshSettings(false, 30), stored.refresh)
    assertEquals(listOf(stored to true), scheduler.applied)
    assertEquals(0, scheduler.syncNowCalls)
    assertTrue(widgets.refreshes >= 1)
  }
}
