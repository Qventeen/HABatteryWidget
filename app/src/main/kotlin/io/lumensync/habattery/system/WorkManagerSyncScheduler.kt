package io.lumensync.habattery.system

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import io.lumensync.habattery.domain.RefreshPolicy
import io.lumensync.habattery.domain.model.Settings
import io.lumensync.habattery.domain.port.SyncScheduler
import java.util.concurrent.TimeUnit

/** Periodic sync in auto mode, and the one-time sync behind ↻. */
class WorkManagerSyncScheduler(
  context: Context,
  private val hasWidgets: suspend () -> Boolean,
) : SyncScheduler {
  private val context = context.applicationContext

  override suspend fun apply(settings: Settings, settingsChanged: Boolean) {
    val workManager = WorkManager.getInstance(context)
    val minutes = RefreshPolicy.periodicIntervalMinutes(settings, hasWidgets())
    if (minutes == null) {
      workManager.cancelUniqueWork(PERIODIC_WORK)
      return
    }
    val request =
      PeriodicWorkRequestBuilder<SyncWorker>(minutes.toLong(), TimeUnit.MINUTES)
        .setConstraints(
          Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        )
        .build()
    val policy =
      if (settingsChanged) ExistingPeriodicWorkPolicy.UPDATE else ExistingPeriodicWorkPolicy.KEEP
    workManager.enqueueUniquePeriodicWork(PERIODIC_WORK, policy, request)
  }

  /** No network constraint: without a network the run fails fast and the widget shows the error. */
  override fun syncNow() {
    val request = OneTimeWorkRequestBuilder<SyncWorker>().build()
    WorkManager.getInstance(context)
      .enqueueUniqueWork(NOW_WORK, ExistingWorkPolicy.REPLACE, request)
  }

  private companion object {
    const val PERIODIC_WORK = "battery_sync_periodic"
    const val NOW_WORK = "battery_sync_now"
  }
}
