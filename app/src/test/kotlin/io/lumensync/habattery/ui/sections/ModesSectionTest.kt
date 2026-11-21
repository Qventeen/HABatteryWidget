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
  fun nowLine_mapped() =
    assertEquals("Now: “Mains” → main", nowLine(resources, builtIn, "Mains"))

  @Test
  fun nowLine_unmapped_showsOtherLabel() =
    assertEquals("Now: “Line” → other (HA text)", nowLine(resources, builtIn, "Line"))

  @Test
  fun nowLine_noText_isNull() = assertNull(nowLine(resources, builtIn, null))
}
