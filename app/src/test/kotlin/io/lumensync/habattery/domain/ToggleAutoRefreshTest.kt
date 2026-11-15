package io.lumensync.habattery.domain

import io.lumensync.habattery.domain.model.RefreshSettings
import io.lumensync.habattery.testing.FakeSettingsRepository
import io.lumensync.habattery.testing.FakeSyncScheduler
import io.lumensync.habattery.testing.FakeWidgetRefresher
import io.lumensync.habattery.testing.configuredSettings
import kotlinx.coroutines.test.runTest
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
