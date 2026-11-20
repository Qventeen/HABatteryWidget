package io.lumensync.habattery.format

import io.lumensync.habattery.domain.LevelSpan.Anything
import io.lumensync.habattery.domain.LevelSpan.AtOrAbove
import io.lumensync.habattery.domain.LevelSpan.AtOrBelow
import io.lumensync.habattery.domain.LevelSpan.Between
import io.lumensync.habattery.domain.model.LevelSide.ABOVE
import io.lumensync.habattery.domain.model.LevelSide.BELOW
import io.lumensync.habattery.domain.model.LevelSide.MIDDLE
import io.lumensync.habattery.domain.model.LevelValue
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

  @Test
  fun spanText_bothEndsShowTheSame_isSingle() =
    assertEquals(SpanText.Single("41"), Between(41.0, 41.0).spanText(0, us))

  @Test
  fun spanText_range_usesTheLocale() =
    assertEquals(SpanText.Range("20,1", "49,9"), Between(20.1, 49.9).spanText(1, uk))

  @Test
  fun spanText_openEnds() {
    assertEquals(SpanText.AtMost("64"), AtOrBelow(64.0).spanText(0, us))
    assertEquals(SpanText.AtLeast("0"), AtOrAbove(0.0).spanText(0, us))
  }

  @Test
  fun spanText_anything_isNull() = assertNull(Anything.spanText(0, us))

  @Test
  fun formatEdge_valuePrecisionWithoutTrailingZeros() {
    assertEquals("20", LevelValue.SOC.formatEdge(20.0, us))
    assertEquals("20", LevelValue.CHARGE_CURRENT.formatEdge(20.0, us))
    assertEquals("51.75", LevelValue.VOLTAGE.formatEdge(51.75000000000001, us))
    assertEquals("51,2", LevelValue.VOLTAGE.formatEdge(51.2, uk))
  }
}
