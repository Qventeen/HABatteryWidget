package io.lumensync.habattery.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

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
}
