package io.lumensync.habattery.system

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import io.lumensync.habattery.domain.DefaultLevels
import io.lumensync.habattery.domain.LevelAlert
import io.lumensync.habattery.domain.LevelBasis
import io.lumensync.habattery.domain.LevelSpan
import io.lumensync.habattery.domain.model.LevelName
import io.lumensync.habattery.domain.model.LevelValue
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowNotificationManager

/** A system that refuses the post although `canPost()` said yes: the permission was revoked between. */
@Implements(NotificationManager::class)
class RefusingNotificationManager : ShadowNotificationManager() {
  var refused = false

  @Implementation
  override fun notify(tag: String?, id: Int, notification: Notification) {
    refused = true
    throw SecurityException("revoked")
  }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], shadows = [RefusingNotificationManager::class])
class AndroidLevelNotifierRefusalTest {
  private val app: Application = ApplicationProvider.getApplicationContext()

  @Test
  fun notifyThrowsSecurityException_postDoesNotCrash() {
    shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    val level =
      DefaultLevels.of(LevelValue.SOC, LevelBasis(null, null)).single { it.defaultName == LevelName.LOW }
    val notifier = AndroidLevelNotifier(app)
    assertTrue(notifier.canPost())

    notifier.post(LevelAlert(LevelValue.SOC, level, LevelSpan.AtOrBelow(20.0), 20.0, 0L))

    val shadow = shadowOf(app.getSystemService(NotificationManager::class.java))
    assertTrue((shadow as RefusingNotificationManager).refused)
  }
}
