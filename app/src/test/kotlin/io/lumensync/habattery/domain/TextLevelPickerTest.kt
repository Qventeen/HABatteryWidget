package io.lumensync.habattery.domain

import io.lumensync.habattery.domain.model.CustomTextLevels
import io.lumensync.habattery.domain.model.Settings
import io.lumensync.habattery.domain.model.TextLevel
import io.lumensync.habattery.domain.model.TextValue
import io.lumensync.habattery.domain.model.withCustomTextLevels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TextLevelPickerTest {
  private val builtIn = DefaultTextLevels.of(TextValue.MODE)

  private fun List<TextLevel>.withTexts(id: Int, vararg texts: String) =
    map { if (it.id == id) it.copy(texts = texts.toList()) else it }

  @Test
  fun match_trimsAndIgnoresCase() {
    assertEquals(3, TextLevelPicker.match(builtIn, "  mains ")!!.id)
    assertEquals(4, TextLevelPicker.match(builtIn, "OFF-GRID")!!.id)
  }

  @Test
  fun match_secondText() =
    assertEquals(3, TextLevelPicker.match(builtIn.withTexts(3, "Mains", "Line"), "LINE")!!.id)

  @Test
  fun match_unknownText_isOther() = assertTrue(TextLevelPicker.match(builtIn, "Eco")!!.other)

  @Test
  fun match_noValue_isNull() {
    listOf(null, "", "unavailable", " Unknown ").forEach {
      assertNull(it, TextLevelPicker.match(builtIn, it))
    }
  }

  @Test
  fun ownLevels_offOrEmpty_isNull() {
    assertNull(TextLevelPicker.ownLevels(CustomTextLevels(on = false, levels = builtIn)))
    assertNull(TextLevelPicker.ownLevels(CustomTextLevels(on = true)))
  }

  @Test
  fun ownLevels_brokenList_isNull() {
    val twoOthers = builtIn + builtIn.last().copy(id = 9)
    val noOther = builtIn.dropLast(1)
    val sameTextTwice = builtIn.withTexts(5, "Mains")
    listOf(twoOthers, noOther, sameTextTwice).forEach {
      assertNull(TextLevelPicker.ownLevels(CustomTextLevels(on = true, levels = it)))
    }
  }

  @Test
  fun effective_noOwnList_isBuiltIn() =
    assertEquals(builtIn, TextLevelPicker.effective(TextValue.MODE, Settings()))

  @Test
  fun effective_ownList_otherLast() {
    val stored = listOf(builtIn.last()) + builtIn.dropLast(1)
    val settings =
      Settings().withCustomTextLevels(TextValue.MODE, CustomTextLevels(on = true, levels = stored))

    assertEquals(builtIn, TextLevelPicker.effective(TextValue.MODE, settings))
  }
}
