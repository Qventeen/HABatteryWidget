package io.lumensync.habattery.system

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import io.lumensync.habattery.container
import io.lumensync.habattery.domain.model.FetchResult
import io.lumensync.habattery.domain.model.PartResult
import io.lumensync.habattery.domain.model.SyncError

/** Runs the one sync path in the background. An exception from the sync fails this run. */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
  override suspend fun doWork(): Result =
    applicationContext.container.syncBattery().toWorkResult(runAttemptCount)
}

private const val MAX_ATTEMPTS = 3

/**
 * Retries only when a part failed with [SyncError.Unreachable], up to 3 times. Every other result
 * is a success, so the periodic work goes on.
 */
internal fun FetchResult.toWorkResult(runAttemptCount: Int): ListenableWorker.Result {
  val unreachable = PartResult.Failed(SyncError.Unreachable) in listOf(battery, inverter)
  return if (unreachable && runAttemptCount < MAX_ATTEMPTS) {
    ListenableWorker.Result.retry()
  } else {
    ListenableWorker.Result.success()
  }
}
