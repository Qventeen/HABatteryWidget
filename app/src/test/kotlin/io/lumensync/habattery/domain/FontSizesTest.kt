package io.lumensync.habattery.domain

import io.lumensync.habattery.domain.model.FontGroup
import io.lumensync.habattery.domain.model.FontSizes
import org.junit.Assert.assertEquals
import org.junit.Test

class FontSizesTest {
  @Test
  fun `defaults match the groups`() =
    FontGroup.entries.forEach { assertEquals(it.name, it.default, FontSizes()[it]) }

  @Test
  fun `clamped brings values into range`() {
    val c = FontSizes(soc = 99f, underSoc = 1f, main = Float.NaN, cell = 15f, small = 9.5f).clamped()
    assertEquals(FontSizes(soc = 42f, underSoc = 10f, main = 22f, cell = 15f, small = 9.5f), c)
  }

  @Test
  fun `clamped replaces infinity with the default`() =
    assertEquals(40f, FontSizes(soc = Float.POSITIVE_INFINITY).clamped().soc)

  @Test
  fun `effective follows the font scale up to the max`() {
    assertEquals(11.55f, FontSizes().effective(FontGroup.SMALL, 1.1f), 0.001f)
    assertEquals(42f, FontSizes().effective(FontGroup.SOC, 2.0f))
    assertEquals(12f, FontSizes(small = 12f).effective(FontGroup.SMALL, 2.0f))
    assertEquals(40f, FontSizes().effective(FontGroup.SOC, 1.0f))
  }

  @Test fun `with sets one group`() = assertEquals(18f, FontSizes().with(FontGroup.MAIN, 18f).main)
}
