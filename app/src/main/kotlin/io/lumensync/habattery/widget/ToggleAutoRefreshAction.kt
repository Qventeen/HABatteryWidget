package io.lumensync.habattery.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import io.lumensync.habattery.container

/** The auto-refresh toggle on the widget. */
class ToggleAutoRefreshAction : ActionCallback {
  override suspend fun onAction(
    context: Context,
    glanceId: GlanceId,
    parameters: ActionParameters,
  ) {
    context.container.toggleAutoRefresh()
  }
}
