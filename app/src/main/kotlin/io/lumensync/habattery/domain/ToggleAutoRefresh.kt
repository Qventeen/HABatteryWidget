package io.lumensync.habattery.domain

import io.lumensync.habattery.domain.port.SettingsRepository
import io.lumensync.habattery.domain.port.SyncScheduler
import io.lumensync.habattery.domain.port.WidgetRefresher
import kotlinx.coroutines.flow.first

/** Turns periodic sync on or off (the widget's toggle and the app card's). Turning on also syncs now. */
class ToggleAutoRefresh(
  private val settings: SettingsRepository,
  private val scheduler: SyncScheduler,
  private val widgets: WidgetRefresher,
) {
  suspend operator fun invoke() {
    settings.update { it.copy(refresh = it.refresh.toggled()) }
    val stored = settings.settings.first()
    widgets.refreshAll()
    scheduler.apply(stored, settingsChanged = true)
    if (stored.refresh.auto) scheduler.syncNow()
  }
}
