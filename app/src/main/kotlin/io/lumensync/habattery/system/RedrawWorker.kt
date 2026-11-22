package io.lumensync.habattery.system

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import io.lumensync.habattery.container
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * Redraws both widgets from local data, with no network, so a green dot can turn amber. The redraw
 * asks for the next one, which replaces and so cancels this work; it still ends, or a widget would
 * miss its redraw.
 */
class RedrawWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
  override suspend fun doWork(): Result {
    withContext(NonCancellable) { applicationContext.container.widgetRefresher.refreshAll() }
    return Result.success()
  }
}
