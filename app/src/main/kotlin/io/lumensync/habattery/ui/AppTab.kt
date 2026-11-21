package io.lumensync.habattery.ui

import io.lumensync.habattery.widget.BatteryWidgetReceiver
import io.lumensync.habattery.widget.InverterWidgetReceiver

/** The app's tabs, in bottom-bar order (spec 0012). */
enum class AppTab {
  BATTERY,
  INVERTER,
  COMMON;

  companion object {
    /** The intent extra of a notification that opens the app on one tab; its value is an [AppTab] name. */
    const val EXTRA_OPEN_TAB = "io.lumensync.habattery.extra.OPEN_TAB"

    /** The tab whose enum name is [name]; null when there is none. */
    fun named(name: String?): AppTab? = entries.firstOrNull { it.name == name }
  }
}

object AppTabs {
  /**
   * The tab the app opens on (spec 0012). [tappedProvider] is the provider class name of the widget
   * the app was opened for (placed, reconfigured or tapped), or null. A widget opens its own tab,
   * even before the connection is set up. Otherwise the [requested] tab (an alert's) wins, and
   * with none a missing URL or token opens Common.
   */
  fun first(tappedProvider: String?, connectionMissing: Boolean, requested: AppTab? = null): AppTab =
    when (tappedProvider) {
      InverterWidgetReceiver::class.java.name -> AppTab.INVERTER
      BatteryWidgetReceiver::class.java.name -> AppTab.BATTERY
      else -> requested ?: if (connectionMissing) AppTab.COMMON else AppTab.BATTERY
    }
}

/**
 * A request to show the tab of the widget from [provider] (null: none named), or the [requested]
 * tab of a notification (null: none). The tab needs the settings and the token, so the screen
 * resolves it with [AppTabs.first] once the first state arrives. Like [PreviewRequest], a widget tap while the app is open makes a new one with a higher
 * [count].
 */
data class AppTabRequest(val provider: String?, val count: Int = 0, val requested: AppTab? = null) {
  /**
   * The request after an intent that names the widget from [named] or asks for a tab; neither
   * keeps this one.
   */
  fun next(named: String?, requested: AppTab? = null): AppTabRequest =
    if (named == null && requested == null) this else AppTabRequest(named, count + 1, requested)

  /**
   * The screen's tab once this request reaches a screen that shows [current] and last saw
   * [seenCount]. Only an unseen request with a count above 0 switches the tab: a new activity
   * (rotation, language) starts at count 0, so a recreation keeps the user's own tab.
   */
  fun tabFor(current: AppTab, seenCount: Int, connectionMissing: Boolean): AppTab =
    if (count != seenCount && count > 0) AppTabs.first(provider, connectionMissing, requested) else current
}
