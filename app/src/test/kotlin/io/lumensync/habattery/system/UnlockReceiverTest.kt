package io.lumensync.habattery.system

import android.app.Application
import android.content.Intent
import android.content.IntentFilter
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class UnlockReceiverTest {
  private val app: Application = ApplicationProvider.getApplicationContext()
  private var calls = 0

  private fun send(action: String) {
    ContextCompat.registerReceiver(
      app,
      UnlockReceiver { calls++ },
      IntentFilter(action),
      ContextCompat.RECEIVER_NOT_EXPORTED,
    )
    app.sendBroadcast(Intent(action))
    shadowOf(Looper.getMainLooper()).idle()
  }

  @Test
  fun userPresent_callsOnce() {
    send(Intent.ACTION_USER_PRESENT)
    assertEquals(1, calls)
  }

  /** Registered for ACTION_SCREEN_ON on purpose: the receiver must filter by action itself. */
  @Test
  fun otherAction_ignored() {
    send(Intent.ACTION_SCREEN_ON)
    assertEquals(0, calls)
  }
}
