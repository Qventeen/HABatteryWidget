package io.lumensync.habattery.system

import android.Manifest
import android.app.PendingIntent
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import androidx.annotation.DrawableRes
import androidx.annotation.RawRes
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.toArgb
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import io.lumensync.habattery.MainActivity
import io.lumensync.habattery.R
import io.lumensync.habattery.domain.LevelAlert
import io.lumensync.habattery.domain.ModeAlert
import io.lumensync.habattery.domain.model.LevelType
import io.lumensync.habattery.domain.model.LevelValue
import io.lumensync.habattery.domain.port.LevelNotifier
import io.lumensync.habattery.format.Formatters
import io.lumensync.habattery.format.modeWord
import io.lumensync.habattery.format.notificationValueRes
import io.lumensync.habattery.format.placeWithUnit
import io.lumensync.habattery.format.shownName
import io.lumensync.habattery.format.unitLabel
import io.lumensync.habattery.widget.WidgetPalette
import kotlin.math.roundToInt

/** The boolean intent extra of an inverter notification's tap; the app opens its Inverter tab. */
const val EXTRA_INVERTER_ALERT = "io.lumensync.habattery.extra.INVERTER_ALERT"

/**
 * Posts level and inverter mode notifications; the levels use one channel per level type. Never
 * asks for the permission; the screen does that.
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
        .setSmallIcon(smallIcon(alert.value))
        .setColor(WidgetPalette.Light.shades(type.defaultColor).ring.toArgb())
        .setContentTitle(title(alert))
        .setContentText(text(alert))
        .setContentIntent(openApp(BASE_ID + alert.value.ordinal, isInverterAlert(alert.value)))
        .setAutoCancel(true)
        .setCategory(NotificationCompat.CATEGORY_STATUS)
        .build()
    try {
      createChannels(manager)
      // One notification per value: a newer level of the same value replaces the older one.
      manager.notify(BASE_ID + alert.value.ordinal, notification)
    } catch (_: SecurityException) {
      // The permission was revoked between the check and the post; nothing to show.
    }
  }

  override fun postMode(alert: ModeAlert) {
    if (!canPost()) return
    val type = alert.level.type
    val manager = NotificationManagerCompat.from(context)
    val word = modeWord(context.resources, alert.level, alert.text)
    val notification =
      NotificationCompat.Builder(context, channelId(type))
        .setSmallIcon(R.drawable.ic_notify_inverter)
        .setColor(WidgetPalette.Light.shades(alert.level.shownColor).ring.toArgb())
        .setContentTitle(context.getString(R.string.notification_mode_title, word))
        .setContentText(
          context.getString(
            R.string.notification_mode_text,
            formatters.updatedAt(alert.measuredAtMillis),
          )
        )
        .setContentIntent(openApp(MODE_NOTIFICATION_ID, inverterAlert = true))
        .setAutoCancel(true)
        .setCategory(NotificationCompat.CATEGORY_STATUS)
        .build()
    try {
      createChannels(manager)
      // One slot: a newer mode replaces the older notification.
      manager.notify(MODE_NOTIFICATION_ID, notification)
    } catch (_: SecurityException) {
      // The permission was revoked between the check and the post; nothing to show.
    }
  }

  /**
   * All four level channels are created again before each post: the owner can set every type's
   * sound at once, and the names follow the current language.
   */
  private fun createChannels(manager: NotificationManagerCompat) {
    OLD_CHANNEL_IDS.forEach(manager::deleteNotificationChannel)
    manager.createNotificationChannelsCompat(LevelType.entries.map(::channel))
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
      LevelValue.GRID_VOLTAGE,
      LevelValue.OUTPUT_VOLTAGE -> formatters.wholeVolts(shown)
    }

  /** The inverter values are the ones the app shows on its Inverter tab. */
  private fun isInverterAlert(value: LevelValue): Boolean = value in LevelValue.INVERTER

  /**
   * Opens the app; [inverterAlert] sets [EXTRA_INVERTER_ALERT]. A PendingIntent is told apart by
   * its request code and not by its extras, so each notification passes its own id: a shared code
   * would let the newest notification's extra overwrite the older one's.
   */
  private fun openApp(requestCode: Int, inverterAlert: Boolean): PendingIntent =
    PendingIntent.getActivity(
      context,
      requestCode,
      Intent(context, MainActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        .putExtra(EXTRA_INVERTER_ALERT, inverterAlert),
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

  private fun channel(type: LevelType): NotificationChannelCompat {
    val builder =
      NotificationChannelCompat.Builder(channelId(type), importance(type))
        .setName(context.getString(channelName(type)))
    vibration(type)?.let { builder.setVibrationEnabled(true).setVibrationPattern(it) }
    sound(type)?.let { builder.setSound(resourceUri(it), SOUND_ATTRIBUTES) }
    return builder.build()
  }

  /**
   * By name, not by number: the channel keeps this address after app updates, and a resource
   * number can change between builds.
   */
  private fun resourceUri(@RawRes id: Int): Uri =
    Uri.Builder()
      .scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
      .authority(context.packageName)
      .appendPath(context.resources.getResourceTypeName(id))
      .appendPath(context.resources.getResourceEntryName(id))
      .build()

  private fun channelId(type: LevelType): String =
    when (type) {
      LevelType.CRITICAL -> "level_critical_2"
      LevelType.WARNING -> "level_warning_2"
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

  /** Critical and Warning can be told apart in a pocket; Normal and Info buzz as the phone does. */
  private fun vibration(type: LevelType): LongArray? =
    when (type) {
      LevelType.CRITICAL -> longArrayOf(0, 500, 200, 500, 200, 500)
      LevelType.WARNING -> longArrayOf(0, 150, 150, 150)
      LevelType.NORMAL,
      LevelType.INFO -> null
    }

  /** Only Critical has its own tone, so it stands out unseen; the others play the phone's. */
  @RawRes
  private fun sound(type: LevelType): Int? =
    when (type) {
      LevelType.CRITICAL -> R.raw.level_critical
      LevelType.WARNING,
      LevelType.NORMAL,
      LevelType.INFO -> null
    }

  @StringRes
  private fun channelName(type: LevelType): Int =
    when (type) {
      LevelType.CRITICAL -> R.string.notification_channel_critical
      LevelType.WARNING -> R.string.notification_channel_warning
      LevelType.NORMAL -> R.string.notification_channel_normal
      LevelType.INFO -> R.string.notification_channel_info
    }

  /**
   * The glyph names the widget, not the value: a battery temperature shows the battery glyph
   * (spec 0015).
   */
  @DrawableRes
  private fun smallIcon(value: LevelValue): Int =
    if (isInverterAlert(value)) R.drawable.ic_notify_inverter else R.drawable.ic_notify_battery

  private companion object {
    /**
     * Android keeps a channel's sound and vibration once it exists, so a new default needs a new
     * id; these retired ids are deleted.
     */
    val OLD_CHANNEL_IDS = listOf("full_charge", "level_critical", "level_warning")

    val SOUND_ATTRIBUTES: AudioAttributes =
      AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    const val BASE_ID = 100

    /** Above every level value's id (`BASE_ID` + ordinal); the mode has one slot. */
    const val MODE_NOTIFICATION_ID = 150
  }
}
