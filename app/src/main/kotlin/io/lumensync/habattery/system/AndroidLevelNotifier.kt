package io.lumensync.habattery.system

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.toArgb
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import io.lumensync.habattery.MainActivity
import io.lumensync.habattery.R
import io.lumensync.habattery.domain.LevelAlert
import io.lumensync.habattery.domain.model.LevelType
import io.lumensync.habattery.domain.model.LevelValue
import io.lumensync.habattery.domain.port.LevelNotifier
import io.lumensync.habattery.format.Formatters
import io.lumensync.habattery.format.notificationValueRes
import io.lumensync.habattery.format.placeWithUnit
import io.lumensync.habattery.format.shownName
import io.lumensync.habattery.format.unitLabel
import io.lumensync.habattery.widget.WidgetPalette
import kotlin.math.roundToInt

/**
 * Posts level notifications on one channel per level type. Never asks for the permission; the
 * screen does that.
 */
class AndroidLevelNotifier(context: Context) : LevelNotifier {
  private val context = context.applicationContext
  private val formatters = Formatters(this.context)

  override fun canPost(): Boolean =
    NotificationManagerCompat.from(context).areNotificationsEnabled() &&
      (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
          PackageManager.PERMISSION_GRANTED)

  override fun post(alert: LevelAlert) {
    if (!canPost()) return
    val type = alert.level.type
    val manager = NotificationManagerCompat.from(context)
    val notification =
      NotificationCompat.Builder(context, channelId(type))
        .setSmallIcon(icon(type))
        .setColor(WidgetPalette.Light.shades(type.defaultColor).ring.toArgb())
        .setContentTitle(title(alert))
        .setContentText(text(alert))
        .setContentIntent(openApp())
        .setAutoCancel(true)
        .setCategory(NotificationCompat.CATEGORY_STATUS)
        .build()
    try {
      // All four are created again before each post: the owner can set every type's sound at
      // once, and the names follow the current language.
      manager.deleteNotificationChannel(OLD_CHANNEL_ID)
      manager.createNotificationChannelsCompat(
        LevelType.entries.map {
          NotificationChannelCompat.Builder(channelId(it), importance(it))
            .setName(context.getString(channelName(it)))
            .build()
        }
      )
      // One notification per value: a newer level of the same value replaces the older one.
      manager.notify(BASE_ID + alert.value.ordinal, notification)
    } catch (_: SecurityException) {
      // The permission was revoked between the check and the post; nothing to show.
    }
  }

  private fun title(alert: LevelAlert): String {
    val level = alert.level
    val value = context.getString(notificationValueRes(alert.value))
    val name = level.shownName(context.resources)
    val locale = context.resources.configuration.locales[0]
    val place =
      alert.span.placeWithUnit(
        alert.value.decimals,
        context.getString(unitLabel(alert.value)),
        locale,
        level.side,
        context.resources,
      )
    return if (place == null) {
      context.getString(R.string.notification_level_title_short, value, name)
    } else {
      context.getString(R.string.notification_level_title, value, name, place)
    }
  }

  private fun text(alert: LevelAlert): String =
    context.getString(
      R.string.notification_level_text,
      shownText(alert.value, alert.shown),
      formatters.updatedAt(alert.measuredAtMillis),
    )

  /** The number as the widget shows that value. */
  private fun shownText(value: LevelValue, shown: Double): String =
    when (value) {
      LevelValue.SOC -> formatters.chargePercent(shown)
      LevelValue.VOLTAGE -> formatters.voltage(shown)
      LevelValue.CHARGE_CURRENT,
      LevelValue.DISCHARGE_CURRENT -> formatters.current(shown)
      LevelValue.DELTA -> formatters.deltaMv(shown.roundToInt())
      LevelValue.BATTERY_TEMPERATURE,
      LevelValue.MOSFET_TEMPERATURE -> formatters.temperature(shown)
    }

  private fun openApp(): PendingIntent =
    PendingIntent.getActivity(
      context,
      0,
      Intent(context, MainActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

  private fun channelId(type: LevelType): String =
    when (type) {
      LevelType.CRITICAL -> "level_critical"
      LevelType.WARNING -> "level_warning"
      LevelType.NORMAL -> "level_normal"
      LevelType.INFO -> "level_info"
    }

  private fun importance(type: LevelType): Int =
    when (type) {
      LevelType.CRITICAL,
      LevelType.WARNING -> NotificationManagerCompat.IMPORTANCE_HIGH
      LevelType.NORMAL,
      LevelType.INFO -> NotificationManagerCompat.IMPORTANCE_DEFAULT
    }

  @StringRes
  private fun channelName(type: LevelType): Int =
    when (type) {
      LevelType.CRITICAL -> R.string.notification_channel_critical
      LevelType.WARNING -> R.string.notification_channel_warning
      LevelType.NORMAL -> R.string.notification_channel_normal
      LevelType.INFO -> R.string.notification_channel_info
    }

  @DrawableRes
  private fun icon(type: LevelType): Int =
    when (type) {
      LevelType.CRITICAL -> R.drawable.ic_notify_critical
      LevelType.WARNING -> R.drawable.ic_notify_warning
      LevelType.NORMAL -> R.drawable.ic_notify_normal
      LevelType.INFO -> R.drawable.ic_notify_info
    }

  private companion object {
    /** The channel of the old full-charge notification. */
    const val OLD_CHANNEL_ID = "full_charge"
    const val BASE_ID = 100
  }
}
