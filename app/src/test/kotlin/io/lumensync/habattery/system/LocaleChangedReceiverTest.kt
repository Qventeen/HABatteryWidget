package io.lumensync.habattery.system

import android.app.Application
import android.content.Intent
import android.content.IntentFilter
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import io.lumensync.habattery.testing.FakeWidgetRefresher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowBroadcastPendingResult
import org.robolectric.shadows.ShadowBroadcastReceiver

@RunWith(RobolectricTestRunner::class)
class LocaleChangedReceiverTest {
  private val app: Application = ApplicationProvider.getApplicationContext()
  private val widgets = FakeWidgetRefresher()

  private fun receiver() = LocaleChangedReceiver({ widgets }, { CoroutineScope(Dispatchers.Unconfined) })

  /** The system starts the app for the broadcast only through a manifest receiver. */
  @Test
  fun manifest_registersTheReceiver_notExported() {
    val intent = Intent(Intent.ACTION_LOCALE_CHANGED).setPackage(app.packageName)
    val found = app.packageManager.queryBroadcastReceivers(intent, 0)
    val info = found.single { it.activityInfo.name == LocaleChangedReceiver::class.java.name }.activityInfo
    assertFalse(info.exported)
  }

  @Test
  fun localeChanged_redrawsTheWidgetsAndFinishes() {
    // Robolectric registers the manifest's receiver too; it would look up App, which unit tests
    // do not start.
    shadowOf(app).getReceiversForIntent(Intent(Intent.ACTION_LOCALE_CHANGED)).forEach(app::unregisterReceiver)
    val receiver = receiver()
    ContextCompat.registerReceiver(
      app,
      receiver,
      IntentFilter(Intent.ACTION_LOCALE_CHANGED),
      ContextCompat.RECEIVER_NOT_EXPORTED,
    )

    app.sendBroadcast(Intent(Intent.ACTION_LOCALE_CHANGED))
    shadowOf(Looper.getMainLooper()).idle()

    assertEquals(1, widgets.refreshes)
    val pending = Shadow.extract<ShadowBroadcastReceiver>(receiver).originalPendingResult
    assertTrue(Shadow.extract<ShadowBroadcastPendingResult>(pending).future.isDone)
  }

  @Test
  fun otherAction_redrawsNothing() {
    receiver().onReceive(app, Intent(Intent.ACTION_TIMEZONE_CHANGED))
    assertEquals(0, widgets.refreshes)
  }
}
