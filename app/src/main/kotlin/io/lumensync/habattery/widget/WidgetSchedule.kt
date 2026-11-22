package io.lumensync.habattery.widget

import android.content.BroadcastReceiver
import android.content.Context
import io.lumensync.habattery.container
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Applies the sync schedule again: the periodic sync depends on whether any widget is placed, so
 * each widget receiver calls this when its first widget is added and its last removed. The work
 * runs past the broadcast, kept alive by [BroadcastReceiver.goAsync].
 */
internal fun BroadcastReceiver.reapplySchedule(context: Context) {
  val pending = goAsync()
  val container = context.container
  container.appScope.launch {
    try {
      container.syncScheduler.apply(
        container.settingsRepository.settings.first(),
        settingsChanged = false,
      )
    } finally {
      pending.finish()
    }
  }
}
