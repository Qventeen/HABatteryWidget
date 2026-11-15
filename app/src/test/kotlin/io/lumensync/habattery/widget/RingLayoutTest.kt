package io.lumensync.habattery.widget

import org.junit.Assert.assertEquals
import org.junit.Test

class RingLayoutTest {
  @Test
  fun defaults_matchTheDesign() {
    // Design v4: label (10.5) · 2 · SOC (40, line-height 1) · 2 · kWh (12), centred in the ring.
    val o = RingLayout.offsets(socDp = 40f, labelDp = 10.5f, underDp = 12f, gapDp = 2f)
    assertEquals(28.14f, o.label, 0.01f)
    assertEquals(29.02f, o.under, 0.01f)
  }

  @Test
  fun scaledDown_movesCloser() {
    val o = RingLayout.offsets(socDp = 34f, labelDp = 8.925f, underDp = 10.2f, gapDp = 1.7f)
    assertEquals(23.92f, o.label, 0.01f)
    assertEquals(24.67f, o.under, 0.01f)
  }
}
