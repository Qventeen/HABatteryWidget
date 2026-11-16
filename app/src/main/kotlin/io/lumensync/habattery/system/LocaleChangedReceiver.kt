package io.lumensync.habattery.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.lumensync.habattery.container
import io.lumensync.habattery.domain.port.WidgetRefresher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Redraws every widget when the app's language changes (spec 0008). Since Android 13 the system
 * sends this broadcast to the one app whose own language changed in Android Settings, and starts
 * the app for it when it is not running. Then no configuration change reaches `App`, and without
 * this the widgets would keep the old language until the next sync. The device language sends it
 * too. While the app runs, `App` has redrawn the widgets already; one more redraw does no harm.
 */
class LocaleChangedReceiver
internal constructor(
  private val widgets: (Context) -> WidgetRefresher,
  private val scope: (Context) -> CoroutineScope,
) : BroadcastReceiver() {
  /** The one the system uses: it builds the receiver from the manifest. */
  constructor() : this({ it.container.widgetRefresher }, { it.container.appScope })

  override fun onReceive(context: Context, intent: Intent) {
    if (intent.action != Intent.ACTION_LOCALE_CHANGED) return
    val pending = goAsync()
    scope(context).launch {
      try {
        widgets(context).refreshAll()
      } finally {
        pending.finish()
      }
    }
  }
}
