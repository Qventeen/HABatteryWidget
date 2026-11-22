package io.lumensync.habattery.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import io.lumensync.habattery.domain.LateRedraw
import io.lumensync.habattery.domain.port.WidgetRefresher

/**
 * Writing the cache or settings does not redraw a widget by itself; this redraws both types. Then it
 * asks for the amber redraw: a live session redraws without running `provideGlance`, where
 * [provideStoredContent] asks for it.
 */
class GlanceWidgetRefresher(context: Context, private val lateRedraw: LateRedraw) : WidgetRefresher {
  private val context = context.applicationContext

  override suspend fun refreshAll() {
    BatteryWidget().updateAll(context)
    InverterWidget().updateAll(context)
    lateRedraw()
  }
}
