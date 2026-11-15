package io.lumensync.habattery.widget

import kotlin.math.hypot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextFitTest {
  /** Every character is half the font size wide; Roboto-like vertical metrics. */
  private object Fake : TextMeasurer {
    override fun width(text: String, sizeDp: Float, weight: TextWeight) = 0.5f * sizeDp * text.length

    override fun metrics(sizeDp: Float, weight: TextWeight) =
      LineMetrics(ascent = -0.93f * sizeDp, descent = 0.24f * sizeDp, top = -1.06f * sizeDp, bottom = 0.27f * sizeDp)

    override fun ink(text: String, sizeDp: Float, weight: TextWeight) =
      Ink(width = width(text, sizeDp, weight), top = -0.7f * sizeDp, bottom = 0f)
  }

  private fun fit(requested: Float, box: Box, vararg texts: String) =
    TextFit.fit(texts.toList(), requested, TextWeight.MEDIUM, box, Fake)

  @Test fun shortValue_keepsTheRequestedSize() = assertEquals(20f, fit(20f, Box(100f, 40f), "10.0 A"), 0f)

  @Test
  fun longValue_shrinksToItsBox() {
    // "125.5 A" is 7 characters: 3.5 × size must be ≤ 100.
    val size = fit(40f, Box(100f, 60f), "125.5 A")
    assertTrue("$size", 3.5f * size <= 100.01f && size > 100f / 3.5f - 0.3f)
  }

  @Test
  fun height_limitsATallRequest() {
    // One line is 1.17 × size high (descent − ascent).
    val size = fit(40f, Box(100f, 23.4f), "1")
    assertTrue("$size", size <= 20f && size > 19.7f)
  }

  @Test
  fun sharedBox_fitsTheSumOfWidths() {
    // 4 + 5 characters share 45 dp: 4.5 × size ≤ 45.
    val size = fit(20f, Box(45f, 40f), "17 h", "11:29")
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
    val baselines = RingStack.baselines(lines, factor, gap = 1f, measurer = Fake)
    return lines.indices.maxOf { i ->
      val size = lines[i].requested * factor
      val ink = Fake.ink(lines[i].text, size, lines[i].weight)
      maxOf(hypot(ink.width / 2, baselines[i] + ink.top), hypot(ink.width / 2, baselines[i] + ink.bottom))
    }
  }

  @Test
  fun ring_roomy_keepsFactorOne() =
    assertEquals(1f, TextFit.fitRing(ringLines(), gap = 1f, innerRadius = 60f, measurer = Fake), 0f)

  @Test
  fun ringLines_shrinkTogether() {
    val lines = ringLines()
    val factor = TextFit.fitRing(lines, gap = 1f, innerRadius = 20f, measurer = Fake)
    assertTrue("$factor", factor < 1f)
    assertTrue(farthest(lines, factor) <= 20f + TextFit.RING_TOLERANCE_DP)
    assertTrue(farthest(lines, factor + 0.02f) > 20f + TextFit.RING_TOLERANCE_DP)
  }

  @Test
  fun ring_toleratesOneDpTouch() {
    val lines = ringLines()
    val atOne = farthest(lines, 1f)
    assertEquals(1f, TextFit.fitRing(lines, gap = 1f, innerRadius = atOne - 0.9f, measurer = Fake), 0f)
    assertTrue(TextFit.fitRing(lines, gap = 1f, innerRadius = atOne - 1.5f, measurer = Fake) < 1f)
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
    val baseline = RingStack.baselines(lines, 1f, gap = 2f, measurer = Fake)[1]
    // Content area 1.17 × 20 centred in a 20 dp box: the baseline is 0.93 × 20 below its top.
    val contentTop = -(1.17f * 20f) / 2
    assertEquals(contentTop + 0.93f * 20f, baseline, 1e-3f)
  }
}
