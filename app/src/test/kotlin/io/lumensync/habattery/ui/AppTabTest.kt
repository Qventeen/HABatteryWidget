package io.lumensync.habattery.ui

import io.lumensync.habattery.ui.AppTab.BATTERY
import io.lumensync.habattery.ui.AppTab.COMMON
import io.lumensync.habattery.ui.AppTab.INVERTER
import io.lumensync.habattery.widget.BatteryWidgetReceiver
import io.lumensync.habattery.widget.InverterWidgetReceiver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class AppTabTest {
  private val battery = BatteryWidgetReceiver::class.java.name
  private val strip = InverterWidgetReceiver::class.java.name

  @Test
  fun tabs_inBarOrder() = assertEquals(listOf(BATTERY, INVERTER, COMMON), AppTab.entries)

  /** A strip opens its own tab, also before the connection is set up. */
  @Test
  fun first_strip_opensInverter() {
    assertEquals(INVERTER, AppTabs.first(strip, connectionMissing = false))
    assertEquals(INVERTER, AppTabs.first(strip, connectionMissing = true))
  }

  @Test
  fun first_batteryWidget_opensBattery() {
    assertEquals(BATTERY, AppTabs.first(battery, connectionMissing = false))
    assertEquals(BATTERY, AppTabs.first(battery, connectionMissing = true))
  }

  @Test
  fun first_noWidget_connectionMissing_opensCommon() =
    assertEquals(COMMON, AppTabs.first(null, connectionMissing = true))

  @Test
  fun first_noWidget_connected_opensBattery() =
    assertEquals(BATTERY, AppTabs.first(null, connectionMissing = false))

  /** A provider the app does not know counts as no widget. */
  @Test
  fun first_unknownProvider_likeNoWidget() {
    assertEquals(COMMON, AppTabs.first("com.example.OtherReceiver", connectionMissing = true))
    assertEquals(BATTERY, AppTabs.first("com.example.OtherReceiver", connectionMissing = false))
  }

  /** An inverter alert asks for its tab (spec 0012); with no request the old rule stays. */
  @Test
  fun first_requestedTab_winsOverTheConnectionRule() {
    assertEquals(INVERTER, AppTabs.first(null, connectionMissing = true, requested = INVERTER))
    assertEquals(INVERTER, AppTabs.first(null, connectionMissing = false, requested = INVERTER))
  }

  @Test
  fun first_noRequestedTab_keepsTheOldRule() =
    assertEquals(COMMON, AppTabs.first(null, connectionMissing = true, requested = null))

  /** A widget id names the tab of its widget; an alert extra cannot come with it, but the widget wins. */
  @Test
  fun first_widgetWinsOverTheRequestedTab() =
    assertEquals(BATTERY, AppTabs.first(battery, connectionMissing = false, requested = INVERTER))

  /** An alert tap while the app is open: a new request, even with no widget named. */
  @Test
  fun next_requestedTab_isANewRequest() =
    assertEquals(AppTabRequest(null, 3, INVERTER), AppTabRequest(battery, 2).next(null, INVERTER))

  @Test
  fun next_nothingNamedOrRequested_keepsTheRequest() {
    val request = AppTabRequest(null, 2, INVERTER)
    assertSame(request, request.next(null, null))
  }

  @Test
  fun tabFor_newRequestedTab_switchesToIt() =
    assertEquals(INVERTER, AppTabRequest(null, 1, INVERTER).tabFor(COMMON, seenCount = 0, connectionMissing = false))

  /** A widget tap while the app is open: a new request with a higher count. */
  @Test
  fun next_namedWidget_isANewRequest() =
    assertEquals(AppTabRequest(strip, 3), AppTabRequest(battery, 2).next(strip))

  /** The launcher icon names no widget: the request stays, so the screen keeps its tab. */
  @Test
  fun next_noneNamed_keepsTheRequest() {
    val request = AppTabRequest(strip, 2)
    assertSame(request, request.next(null))
  }

  /** The first request (count 0) never moves the tab: the screen picked it from the same request. */
  @Test
  fun tabFor_firstRequest_keepsTheTab() =
    assertEquals(COMMON, AppTabRequest(strip).tabFor(COMMON, seenCount = 0, connectionMissing = false))

  @Test
  fun tabFor_newRequest_switchesToTheWidgetsTab() =
    assertEquals(INVERTER, AppTabRequest(strip, 1).tabFor(COMMON, seenCount = 0, connectionMissing = true))

  @Test
  fun tabFor_seenRequest_keepsTheUserTab() =
    assertEquals(COMMON, AppTabRequest(strip, 1).tabFor(COMMON, seenCount = 1, connectionMissing = false))

  /** A strip tap, the user picks a tab, rotation (count 0 again), then a battery widget tap. */
  @Test
  fun tapRotationTap_keepsTheUserTabUntilTheNextTap() {
    var request = AppTabRequest(null)
    var tab = AppTabs.first(request.provider, connectionMissing = false)
    var seen = request.count
    fun arrive(new: AppTabRequest) {
      request = new
      tab = request.tabFor(tab, seen, connectionMissing = false)
      seen = request.count
    }

    arrive(request.next(strip))
    assertEquals(INVERTER, tab)
    tab = COMMON // the user picks a tab
    arrive(AppTabRequest(strip)) // the new activity after rotation
    assertEquals(COMMON, tab)
    arrive(request.next(battery))
    assertEquals(BATTERY, tab)
  }
}
