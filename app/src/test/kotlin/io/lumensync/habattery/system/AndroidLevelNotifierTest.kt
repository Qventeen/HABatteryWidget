package io.lumensync.habattery.system

import android.Manifest
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.media.AudioAttributes
import android.provider.Settings
import androidx.compose.ui.graphics.toArgb
import androidx.test.core.app.ApplicationProvider
import io.lumensync.habattery.domain.DefaultLevels
import io.lumensync.habattery.domain.LevelAlert
import io.lumensync.habattery.domain.LevelBasis
import io.lumensync.habattery.domain.LevelSpan
import io.lumensync.habattery.domain.ModeAlert
import io.lumensync.habattery.domain.model.InverterMode
import io.lumensync.habattery.domain.model.ColorLevel
import io.lumensync.habattery.domain.model.LevelColor
import io.lumensync.habattery.domain.model.LevelName
import io.lumensync.habattery.domain.model.LevelSide
import io.lumensync.habattery.domain.model.LevelType
import io.lumensync.habattery.domain.model.LevelValue
import io.lumensync.habattery.widget.WidgetPalette
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AndroidLevelNotifierTest {
  private val app: Application = ApplicationProvider.getApplicationContext()
  private val manager = app.getSystemService(NotificationManager::class.java)
  private val notifier = AndroidLevelNotifier(app)

  private fun grant() = shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)

  private fun socLevel(name: LevelName) =
    DefaultLevels.of(LevelValue.SOC, LevelBasis(null, null)).single { it.defaultName == name }

  private fun alert(
    value: LevelValue,
    level: ColorLevel,
    span: LevelSpan,
    shown: Double,
  ) = LevelAlert(value, level, span, shown, measuredAtMillis = System.currentTimeMillis())

  private fun low(shown: Double = 20.0) =
    alert(LevelValue.SOC, socLevel(LevelName.LOW), LevelSpan.AtOrBelow(20.0), shown)

  private fun full() =
    alert(LevelValue.SOC, socLevel(LevelName.FULL), LevelSpan.AtOrAbove(100.0), 100.0)

  private fun delta(name: String = "") =
    alert(
      LevelValue.DELTA,
      ColorLevel(id = 1, side = LevelSide.ABOVE, edge = 50.0, type = LevelType.WARNING, name = name),
      LevelSpan.AtOrAbove(50.0),
      55.0,
    )

  private fun id(value: LevelValue) = 100 + value.ordinal

  @Test
  fun post_critical() {
    grant()
    notifier.post(low())

    val posted = shadowOf(manager).getNotification(id(LevelValue.SOC))
    assertEquals("level_critical_2", posted.channelId)
    assertEquals("Charge: Low ↓20 %", posted.extras.getString("android.title"))
    assertTrue(posted.extras.getString("android.text")!!.startsWith("20% · measured at "))
    assertEquals(
      NotificationManager.IMPORTANCE_HIGH,
      manager.getNotificationChannel("level_critical_2").importance,
    )
    assertEquals(1, shadowOf(manager).size())
  }

  private fun modeAlert(text: String) =
    ModeAlert(text, InverterMode.of(text), measuredAtMillis = System.currentTimeMillis())

  @Test
  fun postMode_offGrid() {
    grant()
    notifier.postMode(modeAlert("Off-Grid"))

    val posted = shadowOf(manager).getNotification(150)
    assertEquals("level_warning_2", posted.channelId)
    assertEquals(WidgetPalette.Light.shades(LevelColor.GREEN).ring.toArgb(), posted.color)
    assertEquals("Inverter: battery", posted.extras.getString("android.title"))
    assertTrue(posted.extras.getString("android.text")!!.startsWith("Measured at "))
  }

  @Test
  fun postMode_unknown() {
    grant()
    notifier.postMode(modeAlert("Eco"))

    val posted = shadowOf(manager).getNotification(150)
    assertEquals("level_info", posted.channelId)
    assertEquals("Inverter: Eco", posted.extras.getString("android.title"))
  }

  @Test
  fun postMode_fault() {
    grant()
    notifier.postMode(modeAlert("Fault"))

    assertEquals("level_critical_2", shadowOf(manager).getNotification(150).channelId)
  }

  @Test
  fun postMode_createsEveryLevelChannel() {
    grant()
    notifier.postMode(modeAlert("Mains"))

    assertEquals(
      NotificationManager.IMPORTANCE_HIGH,
      manager.getNotificationChannel("level_critical_2").importance,
    )
  }

  @Test
  fun post_info_quiet() {
    grant()
    notifier.post(full())

    val posted = shadowOf(manager).getNotification(id(LevelValue.SOC))
    assertEquals("level_info", posted.channelId)
    assertTrue(posted.extras.getString("android.title")!!.contains("↑"))
    assertEquals(
      NotificationManager.IMPORTANCE_DEFAULT,
      manager.getNotificationChannel("level_info").importance,
    )
  }

  @Test
  fun onePost_createsEveryLevelChannel() {
    grant()
    notifier.post(full())

    val high = NotificationManager.IMPORTANCE_HIGH
    val default = NotificationManager.IMPORTANCE_DEFAULT
    assertEquals(high, manager.getNotificationChannel("level_critical_2").importance)
    assertEquals(high, manager.getNotificationChannel("level_warning_2").importance)
    assertEquals(default, manager.getNotificationChannel("level_normal").importance)
    assertEquals(default, manager.getNotificationChannel("level_info").importance)
  }

  @Test
  fun critical_ownToneAndThreeLongBuzzes() {
    grant()
    notifier.post(full())

    val channel = manager.getNotificationChannel("level_critical_2")
    assertEquals(
      "android.resource://${app.packageName}/raw/level_critical",
      channel.sound.toString(),
    )
    assertEquals(AudioAttributes.USAGE_NOTIFICATION, channel.audioAttributes.usage)
    assertTrue(channel.shouldVibrate())
    assertArrayEquals(longArrayOf(0, 500, 200, 500, 200, 500), channel.vibrationPattern)
  }

  @Test
  fun warning_phoneSoundAndTwoShortBuzzes() {
    grant()
    notifier.post(full())

    val channel = manager.getNotificationChannel("level_warning_2")
    assertEquals(Settings.System.DEFAULT_NOTIFICATION_URI, channel.sound)
    assertTrue(channel.shouldVibrate())
    assertArrayEquals(longArrayOf(0, 150, 150, 150), channel.vibrationPattern)
  }

  @Test
  fun normalAndInfo_keepThePhoneWay() {
    grant()
    notifier.post(full())

    for (id in listOf("level_normal", "level_info")) {
      val channel = manager.getNotificationChannel(id)
      assertEquals(id, Settings.System.DEFAULT_NOTIFICATION_URI, channel.sound)
      assertFalse(id, channel.shouldVibrate())
      assertNull(id, channel.vibrationPattern)
    }
  }

  @Test
  fun sameValue_replaces() {
    grant()
    notifier.post(low())
    notifier.post(full())
    assertEquals(1, shadowOf(manager).size())

    notifier.post(delta())
    assertEquals(2, shadowOf(manager).size())
  }

  @Test
  fun oldChannels_deleted() {
    grant()
    val old = listOf("full_charge", "level_critical", "level_warning")
    old.forEach {
      manager.createNotificationChannel(
        NotificationChannel(it, it, NotificationManager.IMPORTANCE_HIGH)
      )
    }

    notifier.post(low())

    old.forEach { assertNull(it, manager.getNotificationChannel(it)) }
  }

  @Test
  fun canPost_false_withoutPermission() {
    shadowOf(app).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)

    assertFalse(notifier.canPost())
    notifier.post(low())
    assertEquals(0, shadowOf(manager).size())
  }

  @Test
  fun canPost_true_withPermission() {
    grant()
    assertTrue(notifier.canPost())
  }

  @Test
  fun customName_wins() {
    grant()
    notifier.post(delta(name = "Drift"))

    val title = shadowOf(manager).getNotification(id(LevelValue.DELTA)).extras.getString("android.title")!!
    assertTrue(title, title.startsWith("Cell delta: Drift"))
    assertEquals("Cell delta: Drift ↑50 mV", title)
  }

  @Test
  fun anythingSpan_hasNoPlace() {
    grant()
    notifier.post(alert(LevelValue.DELTA, ColorLevel(id = 2, name = "Any"), LevelSpan.Anything, 5.0))

    val title = shadowOf(manager).getNotification(id(LevelValue.DELTA)).extras.getString("android.title")
    assertEquals("Cell delta: Any", title)
  }

  @Test
  fun noNameNoDefault_showsTheTypeWord() {
    grant()
    notifier.post(
      alert(
        LevelValue.DELTA,
        ColorLevel(id = 3, side = LevelSide.ABOVE, edge = 50.0, type = LevelType.INFO),
        LevelSpan.AtOrAbove(50.0),
        55.0,
      )
    )

    assertEquals("Cell delta: Info ↑50 mV", titleOf(LevelValue.DELTA))
  }

  @Test
  fun middleLevel_showsTheMiddleMark() {
    grant()
    val normal = socLevel(LevelName.NORMAL).copy(notify = true)
    notifier.post(alert(LevelValue.SOC, normal, LevelSpan.Between(41.0, 99.0), 60.0))

    assertEquals("Charge: Normal ↕41–99 %", titleOf(LevelValue.SOC))
  }

  @Test
  fun gridVoltage_wholeVolts_ownSlot() {
    grant()
    val low =
      DefaultLevels.of(LevelValue.GRID_VOLTAGE, LevelBasis(null, null))
        .single { it.defaultName == LevelName.LOW }
    notifier.post(alert(LevelValue.GRID_VOLTAGE, low, LevelSpan.AtOrBelow(206.0), 205.0))

    val posted = shadowOf(manager).getNotification(107)
    assertEquals("Grid input: Low ↓206 V", posted.extras.getString("android.title"))
    assertTrue(posted.extras.getString("android.text")!!.startsWith("205 V · measured at "))
  }

  @Test
  @Config(qualifiers = "uk")
  fun ukrainian_titleAndBody() {
    grant()
    notifier.post(low())

    val posted = shadowOf(manager).getNotification(id(LevelValue.SOC))
    assertEquals("Заряд: Низький ↓20 %", posted.extras.getString("android.title"))
    assertTrue(posted.extras.getString("android.text")!!.startsWith("20% · виміряно о "))
  }

  private fun titleOf(value: LevelValue): String =
    shadowOf(manager).getNotification(id(value)).extras.getString("android.title")!!
}
