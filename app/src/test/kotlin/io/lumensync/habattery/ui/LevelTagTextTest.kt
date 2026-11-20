package io.lumensync.habattery.ui

import io.lumensync.habattery.domain.LevelSpan.Anything
import io.lumensync.habattery.domain.LevelSpan.AtOrAbove
import io.lumensync.habattery.domain.LevelSpan.AtOrBelow
import io.lumensync.habattery.domain.LevelSpan.Between
import io.lumensync.habattery.domain.model.LevelSide.ABOVE
import io.lumensync.habattery.domain.model.LevelSide.BELOW
import io.lumensync.habattery.domain.model.LevelSide.MIDDLE
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LevelTagTextTest {
  private val us = Locale.US
  private val uk = Locale.forLanguageTag("uk")

  @Test
  fun mark_bySide() {
    assertEquals("↓", BELOW.mark())
    assertEquals("↑", ABOVE.mark())
    assertEquals("↕", MIDDLE.mark())
  }

  @Test
  fun placeText_edgeLevels() {
    assertEquals("↓20", AtOrBelow(20.0).placeText(0, us, BELOW))
    assertEquals("↑-10", AtOrAbove(-10.0).placeText(0, us, ABOVE))
  }

  @Test
  fun placeText_middleBetween() {
    assertEquals("↕51.21–55.19", Between(51.21, 55.19).placeText(2, us, MIDDLE))
    assertEquals("↕41", Between(41.0, 41.0).placeText(0, us, MIDDLE))
  }

  @Test
  fun placeText_middleOpenSide() {
    assertEquals("↕≤64", AtOrBelow(64.0).placeText(0, us, MIDDLE))
    assertEquals("↕≥0", AtOrAbove(0.0).placeText(0, us, MIDDLE))
  }

  @Test
  fun placeText_anything_isNull() = assertNull(Anything.placeText(0, us, MIDDLE))

  @Test
  fun numberText_hasNoMark() {
    assertEquals("20,1–49,9", Between(20.1, 49.9).numberText(1, uk, MIDDLE))
    assertEquals("3", AtOrBelow(3.0).numberText(3, us, BELOW))
    assertNull(Anything.numberText(0, us, MIDDLE))
  }

  @Test
  fun numberText_roundsToDecimals() =
    assertEquals("3.201", AtOrAbove(3.2006).numberText(3, us, ABOVE))
}
