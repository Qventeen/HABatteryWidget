package io.lumensync.habattery.domain

import io.lumensync.habattery.domain.model.RefreshSettings
import io.lumensync.habattery.domain.model.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RefreshPolicyTest {
  private fun s(auto: Boolean, interval: Int?, notify: Boolean = false) =
    Settings(refresh = RefreshSettings(auto, interval), notificationsOn = notify)

  @Test
  fun rules() {
    assertNull(RefreshPolicy.periodicIntervalMinutes(s(false, 30), hasWidgets = true))
    assertNull(RefreshPolicy.periodicIntervalMinutes(s(true, 30), hasWidgets = false))
    assertEquals(30, RefreshPolicy.periodicIntervalMinutes(s(true, 30), hasWidgets = true))
    assertEquals(
      30,
      RefreshPolicy.periodicIntervalMinutes(s(true, 30, notify = true), hasWidgets = false),
    )
    assertNull(RefreshPolicy.periodicIntervalMinutes(s(true, null), hasWidgets = true))
    assertNull(RefreshPolicy.periodicIntervalMinutes(s(true, 10), hasWidgets = true))
  }
}
