package com.homeassistant.batterywidget.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.homeassistant.batterywidget.data.BatterySnapshot
import com.homeassistant.batterywidget.data.HomeAssistantClient
import com.homeassistant.batterywidget.data.SettingsStore
import com.homeassistant.batterywidget.data.WidgetRefreshConfig
import com.homeassistant.batterywidget.notifications.BatteryFullNotificationManager
import java.util.concurrent.TimeUnit

/**
 * Fetches the latest battery metrics from Home Assistant, caches them locally,
 * updates Glance DataStore preferences, and refreshes all widget instances.
 */
suspend fun syncAndUpdateAllWidgets(context: Context): Result<BatterySnapshot> {
    val store = SettingsStore(context)
    val config = store.loadConfig()
    val entities = store.loadEntitiesConfig()
    val result = HomeAssistantClient.fetchBatterySnapshot(config, entities)

    val existing = store.loadCachedSnapshot()
    val updatedSnapshot = result.fold(
        onSuccess = { fresh ->
            store.saveCachedSnapshot(fresh)
            BatteryFullNotificationManager.handleFreshSnapshot(context, fresh)
            fresh
        },
        onFailure = { err ->
            val msg = err.message ?: "Помилка з'єднання"
            val fallback = (existing ?: BatterySnapshot(
                socPercent = -1.0,
                powerWatts = 0.0,
                currentAmps = 0.0,
                totalVoltage = 0.0,
                deltaVolts = 0.0,
                minCellIndex = 0,
                maxCellIndex = 0,
                timeStatus = "",
                cellVoltages = emptyList(),
                updatedAtMillis = 0L,
            )).copy(errorMessage = msg)
            store.saveCachedSnapshot(fallback)
            fallback
        },
    )

    pushSnapshotToWidgets(context, updatedSnapshot, isRefreshing = false)
    return result
}

/** Pushes a [BatterySnapshot] and refresh config into Glance DataStore state and updates UI. */
suspend fun pushSnapshotToWidgets(
    context: Context,
    snapshot: BatterySnapshot?,
    isRefreshing: Boolean = false,
) {
    val refreshConfig = SettingsStore(context).loadRefreshConfig()
    val widget = BatteryGlanceWidget()
    val glanceIds = GlanceAppWidgetManager(context).getGlanceIds(BatteryGlanceWidget::class.java)
    for (glanceId in glanceIds) {
        updateAppWidgetState(context, glanceId) { prefs ->
            prefs[BatteryGlanceWidget.KEY_REVISION] = (prefs[BatteryGlanceWidget.KEY_REVISION] ?: 0) + 1
            prefs[BatteryGlanceWidget.KEY_REFRESHING] = isRefreshing
            prefs[BatteryGlanceWidget.KEY_AUTO_REFRESH] = refreshConfig.autoRefreshEnabled
            prefs[BatteryGlanceWidget.KEY_INTERVAL_MINUTES] = refreshConfig.sanitizedIntervalMinutes
            if (snapshot != null) {
                prefs[BatteryGlanceWidget.KEY_SNAPSHOT_JSON] = snapshot.toJsonString()
            }
        }
        widget.update(context, glanceId)
    }
}

class WidgetSyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val fetchResult = syncAndUpdateAllWidgets(applicationContext)
        return if (fetchResult.isSuccess) Result.success() else Result.retry()
    }

    companion object {
        private const val PERIODIC_WORK_NAME = "ha_battery_periodic_sync"
        private const val IMMEDIATE_WORK_NAME = "ha_battery_immediate_sync"
        private const val ACTION_FAST_SYNC_ALARM = "com.homeassistant.batterywidget.ACTION_FAST_SYNC_ALARM"
        private const val ALARM_REQUEST_CODE = 4201

        /**
         * Configures background synchronization based on [WidgetRefreshConfig]:
         * - Manual mode: cancels all periodic workers and alarms.
         * - Interval >= 15 min: uses WorkManager periodic work.
         * - Interval 1..14 min: uses AlarmManager inexact wakeup + immediate WorkManager worker.
         */
        fun applyRefreshSchedule(context: Context, forceReschedule: Boolean) {
            val appContext = context.applicationContext
            val cfg = SettingsStore(appContext).loadRefreshConfig()
            val workManager = WorkManager.getInstance(appContext)

            if (!cfg.autoRefreshEnabled) {
                workManager.cancelUniqueWork(PERIODIC_WORK_NAME)
                cancelFastAlarm(appContext)
                return
            }

            val minutes = cfg.sanitizedIntervalMinutes
            if (minutes >= 15) {
                cancelFastAlarm(appContext)
                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
                val request = PeriodicWorkRequestBuilder<WidgetSyncWorker>(
                    minutes.toLong(),
                    TimeUnit.MINUTES,
                )
                    .setConstraints(constraints)
                    .build()
                val policy = if (forceReschedule) {
                    ExistingPeriodicWorkPolicy.UPDATE
                } else {
                    ExistingPeriodicWorkPolicy.KEEP
                }
                workManager.enqueueUniquePeriodicWork(PERIODIC_WORK_NAME, policy, request)
            } else {
                workManager.cancelUniqueWork(PERIODIC_WORK_NAME)
                scheduleFastAlarm(appContext, minutes, forceReschedule)
            }
        }

        fun cancelAllSync(context: Context) {
            val appContext = context.applicationContext
            WorkManager.getInstance(appContext).cancelUniqueWork(PERIODIC_WORK_NAME)
            cancelFastAlarm(appContext)
        }

        fun enqueueImmediateSync(context: Context) {
            val request = OneTimeWorkRequestBuilder<WidgetSyncWorker>().build()
            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                IMMEDIATE_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }

        private fun alarmIntent(context: Context): Intent =
            Intent(context, SyncAlarmReceiver::class.java).apply {
                action = ACTION_FAST_SYNC_ALARM
            }

        private fun scheduleFastAlarm(context: Context, intervalMinutes: Int, forceReschedule: Boolean) {
            if (!forceReschedule) {
                val existing = PendingIntent.getBroadcast(
                    context,
                    ALARM_REQUEST_CODE,
                    alarmIntent(context),
                    PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
                )
                if (existing != null) return
            }
            val pending = PendingIntent.getBroadcast(
                context,
                ALARM_REQUEST_CODE,
                alarmIntent(context),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val triggerAt = SystemClock.elapsedRealtime() + intervalMinutes * 60_000L
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                triggerAt,
                pending,
            )
        }

        private fun cancelFastAlarm(context: Context) {
            val pending = PendingIntent.getBroadcast(
                context,
                ALARM_REQUEST_CODE,
                alarmIntent(context),
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
            ) ?: return
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            alarmManager?.cancel(pending)
            pending.cancel()
        }
    }
}

/** Triggers background sync for sub-15-minute manual auto-refresh intervals. */
class SyncAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val cfg = SettingsStore(context).loadRefreshConfig()
        if (!cfg.autoRefreshEnabled || cfg.sanitizedIntervalMinutes >= 15) {
            return
        }
        WidgetSyncWorker.applyRefreshSchedule(context, forceReschedule = true)
        WidgetSyncWorker.enqueueImmediateSync(context)
    }
}

/** Zero-argument Glance ActionCallback triggered when tapping the refresh indicator on the widget. */
class RefreshBatteryWidgetAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val store = SettingsStore(context)
        pushSnapshotToWidgets(context, store.loadCachedSnapshot(), isRefreshing = true)
        WidgetSyncWorker.applyRefreshSchedule(context, forceReschedule = false)
        WidgetSyncWorker.enqueueImmediateSync(context)
    }
}
