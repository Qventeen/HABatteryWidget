package io.lumensync.habattery.ui

import io.lumensync.habattery.widget.BatteryWidgetReceiver
import io.lumensync.habattery.widget.InverterWidgetReceiver

/** The app's tabs, in bottom-bar order (spec 0012). */
enum class AppTab {
  BATTERY,
  INVERTER,
  COMMON,
}

object AppTabs {
  /**
   * The tab the app opens on (spec 0012). [tappedProvider] is the provider class name of the widget
   * the app was opened for (placed, reconfigured or tapped), or null. A widget opens its own tab,
   * even before the connection is set up; otherwise a missing URL or token opens Common.
   */
  fun first(tappedProvider: String?, connectionMissing: Boolean): AppTab =
    when (tappedProvider) {
      InverterWidgetReceiver::class.java.name -> AppTab.INVERTER
      BatteryWidgetReceiver::class.java.name -> AppTab.BATTERY
      else -> if (connectionMissing) AppTab.COMMON else AppTab.BATTERY
    }
}

/**
 * A request to show the tab of the widget from [provider] (null: none named). The tab needs the
 * settings and the token, so the screen resolves it with [AppTabs.first] once the first state
 * arrives. Like [PreviewRequest], a widget tap while the app is open makes a new one with a higher
 * [count].
 */
data class AppTabRequest(val provider: String?, val count: Int = 0) {
  /** The request after an intent that names the widget from [named]; none named keeps this one. */
  fun next(named: String?): AppTabRequest = named?.let { AppTabRequest(it, count + 1) } ?: this

  /**
   * The screen's tab once this request reaches a screen that shows [current] and last saw
   * [seenCount]. Only an unseen request with a count above 0 switches the tab: a new activity
   * (rotation, language) starts at count 0, so a recreation keeps the user's own tab.
   */
  fun tabFor(current: AppTab, seenCount: Int, connectionMissing: Boolean): AppTab =
    if (count != seenCount && count > 0) AppTabs.first(provider, connectionMissing) else current
}
