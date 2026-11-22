package io.lumensync.habattery.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/**
 * Hosts [InverterWidget]. Adding the first strip or removing the last can change the schedule. The
 * name is fixed: home screens store it.
 */
class InverterWidgetReceiver : GlanceAppWidgetReceiver() {
  override val glanceAppWidget: GlanceAppWidget = InverterWidget()

  override fun onEnabled(context: Context) {
    super.onEnabled(context)
    reapplySchedule(context)
  }

  override fun onDisabled(context: Context) {
    super.onDisabled(context)
    reapplySchedule(context)
  }
}
