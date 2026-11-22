package io.lumensync.habattery.ui.sections

import android.content.Context
import android.content.res.Resources
import androidx.test.core.app.ApplicationProvider
import io.lumensync.habattery.domain.DefaultTextLevels
import io.lumensync.habattery.domain.model.LevelColor.AMBER
import io.lumensync.habattery.domain.model.LevelColor.BLUE
import io.lumensync.habattery.domain.model.LevelColor.GREEN
import io.lumensync.habattery.domain.model.LevelColor.GREY
import io.lumensync.habattery.domain.model.LevelColor.ORANGE
import io.lumensync.habattery.domain.model.LevelColor.PLAIN
import io.lumensync.habattery.domain.model.LevelColor.RED
import io.lumensync.habattery.domain.model.LevelColor.TEAL
import io.lumensync.habattery.domain.model.LevelColor.VIOLET
import io.lumensync.habattery.domain.model.TextValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en")
class ModesSectionTest {
  private val resources: Resources
    get() = ApplicationProvider.getApplicationContext<Context>().resources

  private val builtIn = DefaultTextLevels.of(TextValue.MODE)

  /** Teal is the Standby colour, so the modes offer it; a mode pill gains nothing from "plain". */
  @Test
  fun modeColors_tealNoPlain() =
    assertEquals(listOf(RED, ORANGE, AMBER, GREEN, TEAL, BLUE, VIOLET, GREY), ModeColors)

  @Test
  fun levelColors_unchanged() =
    assertEquals(listOf(PLAIN, RED, ORANGE, AMBER, GREEN, BLUE, VIOLET, GREY), PickerColors)

  @Test
  fun subLine_listsTexts() {
    val main = builtIn[2].copy(texts = listOf("Mains", "Line"))
    assertEquals("Mains, Line", subLine(resources, main))
  }

  @Test
  fun subLine_other_en() = assertEquals("any other text", subLine(resources, builtIn.last()))

  @Test
  @Config(qualifiers = "uk")
  fun subLine_other_uk() = assertEquals("будь-який інший текст", subLine(resources, builtIn.last()))

  @Test fun nowIndex_mapped() = assertEquals(3, nowIndex(builtIn, "Off-Grid"))

  @Test fun nowIndex_caseAndSpaces() = assertEquals(2, nowIndex(builtIn, " mains "))

  @Test fun nowIndex_unmapped_isOther() = assertEquals(7, nowIndex(builtIn, "Line"))

  @Test fun nowIndex_noText_isNull() = assertNull(nowIndex(builtIn, null))

  @Test fun nowIndex_noValue_isNull() = assertNull(nowIndex(builtIn, "unavailable"))

  @Test
  fun subLine_otherNow_showsLiveText_en() =
    assertEquals("“Line”", subLine(resources, builtIn.last(), nowText = "Line"))

  @Test
  @Config(qualifiers = "uk")
  fun subLine_otherNow_showsLiveText_uk() =
    assertEquals("«Line»", subLine(resources, builtIn.last(), nowText = "Line"))

  @Test
  fun subLine_mappedNow_keepsTexts() =
    assertEquals("Off-Grid", subLine(resources, builtIn[3], nowText = "Off-Grid"))
}
