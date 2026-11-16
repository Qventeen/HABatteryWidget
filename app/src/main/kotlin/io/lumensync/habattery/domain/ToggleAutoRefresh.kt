package io.lumensync.habattery.domain

import io.lumensync.habattery.domain.port.SettingsRepository
import io.lumensync.habattery.domain.port.SyncScheduler
import io.lumensync.habattery.domain.port.WidgetRefresher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Turns periodic sync on or off (the widget's toggle). Turning on also syncs now.
 * Runs are serialized, so a double tap cannot leave the schedule out of step with the setting.
 */
class ToggleAutoRefresh(
  private val settings: SettingsRepository,
  private val scheduler: SyncScheduler,
  private val widgets: WidgetRefresher,
) {
  private val mutex = Mutex()

  suspend operator fun invoke() =
    mutex.withLock {
      settings.update { it.copy(refresh = it.refresh.toggled()) }
      val stored = settings.settings.first()
      widgets.refreshAll()
      scheduler.apply(stored, settingsChanged = true)
      if (stored.refresh.auto) scheduler.syncNow()
    }
}
