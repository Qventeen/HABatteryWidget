package io.lumensync.habattery.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import io.lumensync.habattery.container
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Hosts [BatteryWidget]. Adding the first widget or removing the last can change the schedule. */
class BatteryWidgetReceiver : GlanceAppWidgetReceiver() {
  override val glanceAppWidget: GlanceAppWidget = BatteryWidget()

  override fun onEnabled(context: Context) {
    super.onEnabled(context)
    reapplySchedule(context)
  }

  override fun onDisabled(context: Context) {
    super.onDisabled(context)
    reapplySchedule(context)
  }

  private fun reapplySchedule(context: Context) {
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
}
