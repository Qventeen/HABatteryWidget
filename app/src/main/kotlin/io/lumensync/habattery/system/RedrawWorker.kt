package io.lumensync.habattery.system

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import io.lumensync.habattery.container

/** Redraws both widgets from local data, with no network, so a green dot can turn amber. */
class RedrawWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
  override suspend fun doWork(): Result {
    applicationContext.container.widgetRefresher.refreshAll()
    return Result.success()
  }
}
