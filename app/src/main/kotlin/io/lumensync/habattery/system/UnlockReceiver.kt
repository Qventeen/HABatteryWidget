package io.lumensync.habattery.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Runs [onUnlock] on [scope] when the user unlocks the phone. `USER_PRESENT` reaches only receivers
 * registered at run time, so `App` registers this one and it lives as long as the process. The
 * broadcast stays open until [onUnlock] ends: since Android 14 the system freezes a cached process
 * soon after `onReceive` returns, and the work would stop halfway.
 */
class UnlockReceiver(
  private val scope: CoroutineScope,
  private val onUnlock: suspend () -> Unit,
) : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    if (intent.action != Intent.ACTION_USER_PRESENT) return
    val pending = goAsync()
    scope.launch {
      try {
        onUnlock()
      } finally {
        pending.finish()
      }
    }
  }
}
