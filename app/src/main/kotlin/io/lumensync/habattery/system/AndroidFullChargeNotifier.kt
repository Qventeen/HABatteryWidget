package io.lumensync.habattery.system

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import io.lumensync.habattery.MainActivity
import io.lumensync.habattery.R
import io.lumensync.habattery.domain.port.FullChargeNotifier

/** Posts the 100% notification. Never asks for the permission; the screen does that. */
class AndroidFullChargeNotifier(context: Context) : FullChargeNotifier {
  private val context = context.applicationContext

  override fun showFullCharge() {
    val manager = NotificationManagerCompat.from(context)
    if (!manager.areNotificationsEnabled()) return
    if (
      Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
          PackageManager.PERMISSION_GRANTED
    ) {
      return
    }
    // Created again before each post, so the channel name follows the current language.
    manager.createNotificationChannel(
      NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_HIGH)
        .setName(context.getString(R.string.notification_channel_full))
        .build()
    )
    val notification =
      NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_battery_full_notification)
        .setContentTitle(context.getString(R.string.notification_full_title))
        .setContentText(context.getString(R.string.notification_full_text))
        .setContentIntent(openApp())
        .setAutoCancel(true)
        .setCategory(NotificationCompat.CATEGORY_STATUS)
        .build()
    try {
      manager.notify(NOTIFICATION_ID, notification)
    } catch (_: SecurityException) {
      // The permission was revoked between the check and the post; nothing to show.
    }
  }

  private fun openApp(): PendingIntent =
    PendingIntent.getActivity(
      context,
      0,
      Intent(context, MainActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

  private companion object {
    const val CHANNEL_ID = "full_charge"
    const val NOTIFICATION_ID = 1
  }
}
