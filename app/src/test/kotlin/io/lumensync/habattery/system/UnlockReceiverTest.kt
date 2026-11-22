package io.lumensync.habattery.system

import android.app.Application
import android.content.Intent
import android.content.IntentFilter
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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
class UnlockReceiverTest {
  private val app: Application = ApplicationProvider.getApplicationContext()
  private val scope =
    CoroutineScope(SupervisorJob() + Dispatchers.Unconfined + CoroutineExceptionHandler { _, _ -> })
  private var calls = 0

  private fun send(action: String, onUnlock: suspend () -> Unit = { calls++ }): UnlockReceiver {
    val receiver = UnlockReceiver(scope, onUnlock)
    ContextCompat.registerReceiver(
      app,
      receiver,
      IntentFilter(action),
      ContextCompat.RECEIVER_EXPORTED,
    )
    app.sendBroadcast(Intent(action))
    shadowOf(Looper.getMainLooper()).idle()
    return receiver
  }

  private fun finished(receiver: UnlockReceiver): Boolean {
    val pending = Shadow.extract<ShadowBroadcastReceiver>(receiver).originalPendingResult
    return Shadow.extract<ShadowBroadcastPendingResult>(pending).future.isDone
  }

  @Test
  fun userPresent_keepsTheBroadcastOpenUntilTheWorkEnds() {
    val gate = CompletableDeferred<Unit>()
    val receiver =
      send(Intent.ACTION_USER_PRESENT) {
        gate.await()
        calls++
      }
    assertFalse(finished(receiver))

    gate.complete(Unit)

    assertEquals(1, calls)
    assertTrue(finished(receiver))
  }

  @Test
  fun failingWork_stillFinishes() {
    val receiver = send(Intent.ACTION_USER_PRESENT) { error("disk") }
    assertTrue(finished(receiver))
  }

  /** Registered for ACTION_SCREEN_ON on purpose: the receiver must filter by action itself. */
  @Test
  fun otherAction_ignored() {
    send(Intent.ACTION_SCREEN_ON)
    assertEquals(0, calls)
  }
}
