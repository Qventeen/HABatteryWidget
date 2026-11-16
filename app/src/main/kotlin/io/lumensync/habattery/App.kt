package io.lumensync.habattery

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import io.lumensync.habattery.system.TextConfig
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class App : Application() {
  /** Built on first use: WorkManager may start a worker before [onCreate] runs. */
  val container: AppContainer by lazy { AppContainer(this) }

  /**
   * A new app language or system font scale redraws every widget (a live Glance session would keep
   * the old texts and sizes); before Android 12 a night-mode switch does too ([TextConfig]).
   */
  override fun onConfigurationChanged(newConfig: Configuration) {
    super.onConfigurationChanged(newConfig)
    if (container.configWatcher.changed(TextConfig.of(newConfig))) {
      container.appScope.launch { container.widgetRefresher.refreshAll() }
    }
  }

  override fun onCreate() {
    super.onCreate()
    // KEEP on app start: an existing periodic work keeps its timing.
    container.appScope.launch {
      container.syncScheduler.apply(
        container.settingsRepository.settings.first(),
        settingsChanged = false,
      )
    }
  }
}

val Context.container: AppContainer
  get() = (applicationContext as App).container
