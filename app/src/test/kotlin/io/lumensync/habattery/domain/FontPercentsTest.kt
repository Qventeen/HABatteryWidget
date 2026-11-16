package io.lumensync.habattery.domain

import io.lumensync.habattery.domain.model.FontGroup
import io.lumensync.habattery.domain.model.FontPercents
import org.junit.Assert.assertEquals
import org.junit.Test

class FontPercentsTest {
  @Test
  fun `every group defaults to the design size`() =
    FontGroup.entries.forEach { assertEquals(it.name, 50, FontPercents()[it]) }

  @Test
  fun `clamped keeps 10 to 100`() {
    val c = FontPercents(soc = 9, underSoc = 10, main = 50, cell = 100, small = 101).clamped()
    assertEquals(FontPercents(soc = 10, underSoc = 10, main = 50, cell = 100, small = 100), c)
  }

  @Test fun `with sets one group`() = assertEquals(75, FontPercents().with(FontGroup.MAIN, 75).main)

  @Test
  fun `requested size is the design size times percent over 50 times the font scale`() {
    val design = 22f
    assertEquals(11f, FontPercents(main = 25).requestedDp(FontGroup.MAIN, design, 1f), 1e-4f)
    assertEquals(22f, FontPercents(main = 50).requestedDp(FontGroup.MAIN, design, 1f), 1e-4f)
    assertEquals(44f, FontPercents(main = 100).requestedDp(FontGroup.MAIN, design, 1f), 1e-4f)
    assertEquals(18.7f, FontPercents(main = 50).requestedDp(FontGroup.MAIN, design, 0.85f), 1e-4f)
    assertEquals(57.2f, FontPercents(main = 100).requestedDp(FontGroup.MAIN, design, 1.3f), 1e-4f)
    assertEquals(14.3f, FontPercents(main = 25).requestedDp(FontGroup.MAIN, design, 1.3f), 1e-4f)
  }

  @Test
  fun `requested size reads its own group`() =
    assertEquals(28f, FontPercents(cell = 100).requestedDp(FontGroup.CELL, 14f, 1f), 1e-4f)

  @Test
  fun `small texts start at one and a half times the design`() {
    assertEquals(15.75f, FontPercents().requestedDp(FontGroup.SMALL, 10.5f, 1f), 1e-4f)
    assertEquals(31.5f, FontPercents(small = 100).requestedDp(FontGroup.SMALL, 10.5f, 1f), 1e-4f)
    assertEquals(7.875f, FontPercents(small = 25).requestedDp(FontGroup.SMALL, 10.5f, 1f), 1e-4f)
  }
}
