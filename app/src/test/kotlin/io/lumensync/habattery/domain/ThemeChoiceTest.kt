package io.lumensync.habattery.domain

import io.lumensync.habattery.domain.model.ThemeChoice
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeChoiceTest {
  @Test
  fun system_followsTheSystemMode() {
    assertTrue(ThemeChoice.SYSTEM.isDark(systemDark = true))
    assertFalse(ThemeChoice.SYSTEM.isDark(systemDark = false))
  }

  @Test
  fun lightAndDark_ignoreTheSystemMode() {
    assertFalse(ThemeChoice.LIGHT.isDark(systemDark = true))
    assertTrue(ThemeChoice.DARK.isDark(systemDark = false))
  }
}
