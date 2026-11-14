package io.lumensync.habattery

import android.app.Application
import android.content.Context
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class App : Application() {
  /** Built on first use: WorkManager may start a worker before [onCreate] runs. */
  val container: AppContainer by lazy { AppContainer(this) }

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
