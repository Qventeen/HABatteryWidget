package com.homeassistant.batterywidget.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.homeassistant.batterywidget.MainActivity
import com.homeassistant.batterywidget.R
import com.homeassistant.batterywidget.data.BatteryChargeMode
import com.homeassistant.batterywidget.data.BatteryFormatters
import com.homeassistant.batterywidget.data.BatterySnapshot
import com.homeassistant.batterywidget.data.SettingsStore

data class FullChargeEvalResult(
    val shouldNotify: Boolean,
    val nextNotifiedState: Boolean,
)

/**
 * Pure state machine deciding when to fire a 100% battery full notification
 * and when to re-arm after discharge hysteresis.
 */
object FullChargeNotificationEvaluator {
    const val REARM_SOC_THRESHOLD = 98

    fun isBatteryFull(snapshot: BatterySnapshot): Boolean {
        if (!snapshot.hasValidData || snapshot.errorMessage != null) return false
        return snapshot.socInt >= 100 ||
            BatteryFormatters.formatStatusLine(snapshot).mode == BatteryChargeMode.FULL
    }

    fun evaluate(
        snapshot: BatterySnapshot,
        notifyEnabled: Boolean,
        alreadyNotified: Boolean,
    ): FullChargeEvalResult {
        if (!snapshot.hasValidData || snapshot.errorMessage != null) {
            return FullChargeEvalResult(
                shouldNotify = false,
                nextNotifiedState = alreadyNotified,
            )
        }

        if (isBatteryFull(snapshot)) {
            return FullChargeEvalResult(
                shouldNotify = notifyEnabled && !alreadyNotified,
                nextNotifiedState = if (notifyEnabled) true else alreadyNotified,
            )
        }

        val nextNotified = if (snapshot.socInt <= REARM_SOC_THRESHOLD) false else alreadyNotified
        return FullChargeEvalResult(
            shouldNotify = false,
            nextNotifiedState = nextNotified,
        )
    }
}

/** Manages notification channel, permission checks, and deduplicated 100% charge alerts. */
object BatteryFullNotificationManager {
    private const val CHANNEL_ID = "battery_full_channel"
    private const val NOTIFICATION_ID = 1001
    private val evaluationLock = Any()

    fun hasRuntimePermission(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

    fun canPostNotifications(context: Context): Boolean =
        hasRuntimePermission(context) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.notification_channel_description)
            enableVibration(true)
        }
        manager.createNotificationChannel(channel)
    }

    fun openAppNotificationSettings(context: Context) {
        val appSettingsIntent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val started = runCatching { context.startActivity(appSettingsIntent) }.isSuccess
        if (!started) {
            val detailsIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            runCatching { context.startActivity(detailsIntent) }
        }
    }

    /**
     * Evaluates a freshly fetched [BatterySnapshot] and posts a 100% charge notification
     * at most once per full-charge cycle when enabled.
     */
    fun handleFreshSnapshot(context: Context, snapshot: BatterySnapshot): Boolean {
        val appContext = context.applicationContext
        synchronized(evaluationLock) {
            val store = SettingsStore(appContext)
            val config = store.loadNotificationConfig()
            val eval = FullChargeNotificationEvaluator.evaluate(
                snapshot = snapshot,
                notifyEnabled = config.notifyOnFullEnabled,
                alreadyNotified = config.fullChargeNotified,
            )

            if (eval.shouldNotify) {
                val posted = showFullChargeNotification(appContext, snapshot)
                if (posted) {
                    store.setFullChargeNotified(true)
                }
                return posted
            }

            if (eval.nextNotifiedState != config.fullChargeNotified) {
                store.setFullChargeNotified(eval.nextNotifiedState)
            }
            return false
        }
    }

    private fun showFullChargeNotification(context: Context, snapshot: BatterySnapshot): Boolean {
        if (!canPostNotifications(context)) return false
        ensureChannel(context)

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val voltage = BatteryFormatters.formatTotalVoltage(snapshot.totalVoltage)
        val current = BatteryFormatters.formatCurrent(snapshot.currentAmps)
        val deltaMv = BatteryFormatters.computeDeltaMv(snapshot)
        val updated = BatteryFormatters.formatTimestamp(snapshot.updatedAtMillis)
        val body = "$voltage • $current • Δ $deltaMv mV • $updated"

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_battery_full_notification)
            .setContentTitle(context.getString(R.string.notification_full_title))
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        return try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
            true
        } catch (_: SecurityException) {
            false
        }
    }
}
