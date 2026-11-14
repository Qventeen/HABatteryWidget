package io.lumensync.habattery

import android.app.Application
import android.content.Context
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class App : Application() {
  lateinit var container: AppContainer

  override fun onCreate() {
    super.onCreate()
    container = AppContainer(this)
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
