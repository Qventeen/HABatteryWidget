package io.lumensync.habattery.domain

import io.lumensync.habattery.domain.model.FontPercents
import io.lumensync.habattery.domain.model.HomeScale
import io.lumensync.habattery.domain.model.Settings
import io.lumensync.habattery.domain.model.clamped
import io.lumensync.habattery.domain.model.textSizeChanged
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeScaleTest {
  @Test fun default_is100() = assertEquals(100, Settings().homeScalePercent)

  @Test
  fun clamp_keepsTheRange() {
    assertEquals(80, HomeScale.clamp(79))
    assertEquals(80, HomeScale.clamp(80))
    assertEquals(100, HomeScale.clamp(100))
    assertEquals(150, HomeScale.clamp(150))
    assertEquals(150, HomeScale.clamp(151))
  }

  @Test
  fun factor_isThePercentOver100() {
    assertEquals(1.28f, HomeScale.factor(128), 0.0001f)
    assertEquals(1f, HomeScale.factor(100), 0.0001f)
    assertEquals(0.8f, HomeScale.factor(80), 0.0001f)
  }

  @Test
  fun settingsClamped_clampsBothParts() {
    val c = Settings(fontPercents = FontPercents(soc = 5), homeScalePercent = 200).clamped()
    assertEquals(25, c.fontPercents.soc)
    assertEquals(150, c.homeScalePercent)
  }

  @Test
  fun textSizeChanged_whenAFontIsNot50OrTheScaleIsNot100() {
    assertFalse(Settings().textSizeChanged)
    assertTrue(Settings(fontPercents = FontPercents(small = 55)).textSizeChanged)
    assertTrue(Settings(homeScalePercent = 128).textSizeChanged)
    assertTrue(Settings(fontPercents = FontPercents(cell = 25), homeScalePercent = 90).textSizeChanged)
  }
}
