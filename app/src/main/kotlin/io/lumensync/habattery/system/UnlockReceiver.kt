package io.lumensync.habattery.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Calls [onUnlock] when the user unlocks the phone. `USER_PRESENT` reaches only receivers
 * registered at run time, so `App` registers this one and it lives as long as the process.
 */
class UnlockReceiver(private val onUnlock: () -> Unit) : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    if (intent.action == Intent.ACTION_USER_PRESENT) onUnlock()
  }
}
