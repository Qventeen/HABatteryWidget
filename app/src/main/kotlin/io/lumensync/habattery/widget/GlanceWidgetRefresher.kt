package io.lumensync.habattery.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import io.lumensync.habattery.domain.port.WidgetRefresher

/** Writing the cache or settings does not redraw a widget by itself; this redraws both types. */
class GlanceWidgetRefresher(context: Context) : WidgetRefresher {
  private val context = context.applicationContext

  override suspend fun refreshAll() {
    BatteryWidget().updateAll(context)
    InverterWidget().updateAll(context)
  }
}
