package io.lumensync.habattery.widget

import kotlin.math.hypot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextFitTest {
  private fun fit(requested: Float, box: Box, vararg texts: String) =
    TextFit.fit(texts.toList(), requested, TextWeight.MEDIUM, box, FakeMeasurer)

  @Test fun shortValue_keepsTheRequestedSize() = assertEquals(20f, fit(20f, Box(100f, 40f), "10.0 A"), 0f)

  @Test
  fun longValue_shrinksToItsBox() {
    // "125.5 A" is 7 characters: 3.5 × size must be ≤ 100 less the width slack.
    val room = 100f - TextFit.WIDTH_SLACK_DP
    val size = fit(40f, Box(100f, 60f), "125.5 A")
    assertTrue("$size", 3.5f * size <= room + 0.01f && size > room / 3.5f - 0.3f)
  }

  /**
   * The box must hold the font's top (1.06 × size in the fake) and the ink below the baseline,
   * with [TextFit.HEIGHT_SLACK_DP] to spare.
   */
  @Test
  fun height_limitsATallRequest() {
    val size = fit(40f, Box(100f, 21.2f + TextFit.HEIGHT_SLACK_DP), "1")
    assertTrue("$size", 1.06f * size <= 21.21f && size > 19.7f)
  }

  /** A decimal comma reaches below the baseline (Ukrainian "3,312 В"), so it needs more height. */
  @Test
  fun comma_needsRoomBelowTheBaseline() {
    val box = Box(100f, 12f + TextFit.HEIGHT_SLACK_DP)
    val point = fit(40f, box, "3.312")
    val comma = fit(40f, box, "3,312")
    assertTrue("$comma < $point", comma < point)
    assertTrue("$comma", (1.06f + 0.15f) * comma <= 12.01f)
  }

  @Test
  fun sharedBox_fitsTheSumOfWidths() {
    // 4 + 5 characters share 45 dp (and the width slack): 4.5 × size ≤ 45.
    val size = fit(20f, Box(45f + TextFit.WIDTH_SLACK_DP, 40f), "17 h", "11:29")
    assertTrue("$size", size <= 10f && size > 9.7f)
  }

  @Test fun fit_neverReturnsMoreThanRequested() = assertEquals(12f, fit(12f, Box(1000f, 1000f), "x"), 0f)

  private fun ringLines(soc: Float = 28f) =
    listOf(
      RingLine("SOC", 8.5f, TextWeight.NORMAL, lineHeight = 1.1f),
      RingLine("55", soc, TextWeight.BOLD, lineHeight = 1f),
      RingLine("4.58 kWh", 10f, TextWeight.MEDIUM, lineHeight = 1.2f),
    )

  /** The farthest ink corner of the three lines from the ring centre, at [factor]. */
  private fun farthest(lines: List<RingLine>, factor: Float): Float {
    val baselines = RingStack.baselines(lines, factor, gap = 1f, measurer = FakeMeasurer)
    return lines.indices.maxOf { i ->
      val size = lines[i].requested * factor
      val ink = FakeMeasurer.ink(lines[i].text, size, lines[i].weight)
      maxOf(hypot(ink.width / 2, baselines[i] + ink.top), hypot(ink.width / 2, baselines[i] + ink.bottom))
    }
  }

  @Test
  fun ring_roomy_keepsFactorOne() =
    assertEquals(1f, TextFit.fitRing(ringLines(), gap = 1f, innerRadius = 60f, measurer = FakeMeasurer), 0f)

  @Test
  fun ringLines_shrinkTogether() {
    val lines = ringLines()
    val factor = TextFit.fitRing(lines, gap = 1f, innerRadius = 20f, measurer = FakeMeasurer)
    assertTrue("$factor", factor < 1f)
    assertTrue(farthest(lines, factor) <= 20f + TextFit.RING_TOLERANCE_DP)
    assertTrue(farthest(lines, factor + 0.02f) > 20f + TextFit.RING_TOLERANCE_DP)
  }

  @Test
  fun ring_toleratesOneDpTouch() {
    val lines = ringLines()
    val atOne = farthest(lines, 1f)
    assertEquals(1f, TextFit.fitRing(lines, gap = 1f, innerRadius = atOne - 0.9f, measurer = FakeMeasurer), 0f)
    assertTrue(TextFit.fitRing(lines, gap = 1f, innerRadius = atOne - 1.5f, measurer = FakeMeasurer) < 1f)
  }

  @Test
  fun stack_isCentredOnTheRing() {
    // Equal line boxes above and below: the middle line's box centre sits on the ring centre.
    val lines =
      listOf(
        RingLine("a", 10f, TextWeight.NORMAL, lineHeight = 1f),
        RingLine("b", 20f, TextWeight.NORMAL, lineHeight = 1f),
        RingLine("c", 10f, TextWeight.NORMAL, lineHeight = 1f),
      )
    val baseline = RingStack.baselines(lines, 1f, gap = 2f, measurer = FakeMeasurer)[1]
    // Content area 1.17 × 20 centred in a 20 dp box: the baseline is 0.93 × 20 below its top.
    val contentTop = -(1.17f * 20f) / 2
    assertEquals(contentTop + 0.93f * 20f, baseline, 1e-3f)
  }
}
