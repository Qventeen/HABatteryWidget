package io.lumensync.habattery.format

import android.content.Context
import android.content.res.Resources
import androidx.test.core.app.ApplicationProvider
import io.lumensync.habattery.domain.DefaultTextLevels
import io.lumensync.habattery.domain.model.TextLevel
import io.lumensync.habattery.domain.model.TextValue
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class InverterLabelsTest {
  private val resources: Resources
    get() = ApplicationProvider.getApplicationContext<Context>().resources

  private val main = DefaultTextLevels.of(TextValue.MODE)[2]
  private val other = DefaultTextLevels.of(TextValue.MODE).last()

  @Test
  @Config(qualifiers = "en")
  fun defaultName_en() = assertEquals("main", modeWord(resources, main, "Mains"))

  @Test
  @Config(qualifiers = "uk")
  fun defaultName_uk() = assertEquals("мережа", modeWord(resources, main, "Mains"))

  @Test
  @Config(qualifiers = "en")
  fun ownName_asTyped_en() =
    assertEquals("Grid", modeWord(resources, main.copy(name = "Grid"), "Mains"))

  @Test
  @Config(qualifiers = "uk")
  fun ownName_asTyped_uk() =
    assertEquals("Grid", modeWord(resources, main.copy(name = "Grid"), "Mains"))

  @Test
  @Config(qualifiers = "en")
  fun other_showsLiveTextElseLabel() {
    assertEquals("Eco", modeWord(resources, other, "Eco"))
    assertEquals("other", modeWord(resources, other, null))
  }

  @Test
  @Config(qualifiers = "uk")
  fun other_label_uk() = assertEquals("інший", modeWord(resources, other, null))

  @Test
  @Config(qualifiers = "en")
  fun addedMode_blankName_showsLiveTextElseFirstText() {
    val added = TextLevel(id = 9, texts = listOf("Utility"))
    assertEquals("UTILITY", modeWord(resources, added, "UTILITY"))
    assertEquals("Utility", modeWord(resources, added, null))
  }
}
