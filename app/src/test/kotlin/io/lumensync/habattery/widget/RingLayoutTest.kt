package io.lumensync.habattery.widget

import org.junit.Assert.assertEquals
import org.junit.Test

class RingLayoutTest {
  private val lines =
    listOf(
      RingLine("SOC", 8.5f, TextWeight.NORMAL),
      RingLine("55", 28f, TextWeight.BOLD),
      RingLine("4.58 kWh", 10f, TextWeight.MEDIUM),
    )

  @Test
  fun eachView_putsItsBaselineOnTheCanvasBaseline() {
    val baselines = RingStack.baselines(lines, lines.map { it.requested }, gap = 6f, measurer = FakeMeasurer)
    val centers = RingLayout.centers(lines, gap = 6f, measurer = FakeMeasurer)
    lines.indices.forEach { i ->
      val size = lines[i].requested
      // A view of height bottom − top, centred on its centre, has its baseline at −top below its top.
      val viewTop = centers[i] - (1.06f + 0.27f) * size / 2
      assertEquals(baselines[i], viewTop + 1.06f * size, 1e-3f)
    }
  }

  @Test
  fun lines_keepTheirOrder() {
    val c = RingLayout.centers(lines, gap = 6f, measurer = FakeMeasurer)
    assert(c[0] < c[1] && c[1] < c[2]) { c.toString() }
  }
}
