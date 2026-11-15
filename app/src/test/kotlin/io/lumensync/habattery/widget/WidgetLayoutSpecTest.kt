package io.lumensync.habattery.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetLayoutSpecTest {

  @Test
  fun fromSize_8Cells_fitsCompact2RowsWithoutHidingCells() {
    // 1-row widget: hides cells cleanly
    val mini = WidgetLayoutSpec.fromSize(heightDp = 110f, cellCount = 8)
    assertEquals(WidgetDensityTier.SUMMARY_ONLY, mini.tier)
    assertFalse(mini.showCells)

    // 2-row widget (e.g. 155dp..185dp): compact mode shows all 8 cells with row spacers
    val compact = WidgetLayoutSpec.fromSize(heightDp = 160f, cellCount = 8)
    assertEquals(WidgetDensityTier.COMPACT, compact.tier)
    assertTrue(compact.showCells)
    assertTrue(compact.useRowSpacers)

    // 3-row widget (e.g. 240dp..270dp): regular mode
    val regular = WidgetLayoutSpec.fromSize(heightDp = 250f, cellCount = 8)
    assertEquals(WidgetDensityTier.REGULAR, regular.tier)
    assertTrue(regular.showCells)
    assertTrue(regular.useRowSpacers)

    // Tall 3-4 row widget (>= 275dp): large mode
    val large = WidgetLayoutSpec.fromSize(heightDp = 280f, cellCount = 8)
    assertEquals(WidgetDensityTier.LARGE, large.tier)
    assertTrue(large.showCells)
    assertTrue(large.useRowSpacers)
  }

  @Test
  fun fromSize_16Cells_avoidsClippingAndDisablesSpacersAbove5Rows() {
    val twoRows = WidgetLayoutSpec.fromSize(heightDp = 175f, cellCount = 16)
    assertEquals(WidgetDensityTier.SUMMARY_ONLY, twoRows.tier)
    assertFalse(twoRows.showCells)

    val threeRows = WidgetLayoutSpec.fromSize(heightDp = 240f, cellCount = 16)
    assertEquals(WidgetDensityTier.COMPACT, threeRows.tier)
    assertTrue(threeRows.showCells)
    assertEquals(8, threeRows.cellRows)
    assertFalse(threeRows.useRowSpacers)
  }

  @Test
  fun fromSize_0Cells_neverShowsCellsSection() {
    val spec = WidgetLayoutSpec.fromSize(heightDp = 260f, cellCount = 0)
    assertFalse(spec.showCells)
    assertEquals(0, spec.cellRows)
    assertFalse(spec.useRowSpacers)
  }
}
