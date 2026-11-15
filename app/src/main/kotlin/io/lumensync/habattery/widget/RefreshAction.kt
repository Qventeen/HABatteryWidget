package io.lumensync.habattery.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import io.lumensync.habattery.container

/** The ↻ chip: show "refreshing" now, then hand the network work to WorkManager. */
class RefreshAction : ActionCallback {
  override suspend fun onAction(
    context: Context,
    glanceId: GlanceId,
    parameters: ActionParameters,
  ) {
    val container = context.container
    val now = System.currentTimeMillis()
    container.readingCache.update { it.copy(refreshingSinceMillis = now) }
    container.widgetRefresher.refreshAll()
    container.syncScheduler.syncNow()
  }
}
