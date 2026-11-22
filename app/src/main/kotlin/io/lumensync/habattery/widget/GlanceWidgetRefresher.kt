package io.lumensync.habattery.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import io.lumensync.habattery.domain.LateRedraw
import io.lumensync.habattery.domain.port.WidgetRefresher
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Writing the cache or settings does not redraw a widget by itself; this moves [clockTick] to now,
 * so a live session redraws at the new time too ([drawingNow]), and redraws both types. Then it
 * asks for the amber redraw: a live session redraws without running `provideGlance`, where
 * [provideStoredContent] asks for it.
 */
class GlanceWidgetRefresher(
  context: Context,
  private val lateRedraw: LateRedraw,
  private val clockTick: MutableStateFlow<Long>,
  private val clock: () -> Long,
) : WidgetRefresher {
  private val context = context.applicationContext

  override suspend fun refreshAll() {
    clockTick.value = clock()
    BatteryWidget().updateAll(context)
    InverterWidget().updateAll(context)
    lateRedraw()
  }
}
