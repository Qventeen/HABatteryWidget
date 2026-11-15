package io.lumensync.habattery.widget

import io.lumensync.habattery.widget.WidgetDensityTier.COMPACT
import io.lumensync.habattery.widget.WidgetDensityTier.REGULAR
import io.lumensync.habattery.widget.WidgetDensityTier.SUMMARY_ONLY
import io.lumensync.habattery.widget.WidgetLayoutSpec.Companion.fromSize
import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetLayoutSpecTest {
  @Test
  fun `tiers by height`() {
    assertEquals(SUMMARY_ONLY, fromSize(120f).tier)
    assertEquals(COMPACT, fromSize(180f).tier)
    assertEquals(REGULAR, fromSize(220f).tier)
  }

  @Test
  fun `tier edges`() {
    assertEquals(SUMMARY_ONLY, fromSize(134.9f).tier)
    assertEquals(COMPACT, fromSize(135f).tier)
    assertEquals(COMPACT, fromSize(214.9f).tier)
    assertEquals(REGULAR, fromSize(215f).tier)
    assertEquals(REGULAR, fromSize(400f).tier)
  }
}
