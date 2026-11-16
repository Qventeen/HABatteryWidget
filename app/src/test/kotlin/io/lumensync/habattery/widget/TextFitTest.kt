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

  /** The 4×2 ring lines; [soc] and [under] are the requested sizes, floors at the 50 % sizes. */
  private fun ringLines(soc: Float = 28f, under: String = "4.58 kWh", label: Float = 8.5f, underSize: Float = 10f) =
    listOf(
      RingLine("SOC", label, TextWeight.NORMAL, lineHeight = 1.1f, floor = 8.5f),
      RingLine("55", soc, TextWeight.BOLD, lineHeight = 1f, floor = 28f),
      RingLine(under, underSize, TextWeight.MEDIUM, lineHeight = 1.2f, floor = 10f),
    )

  /** The farthest ink corner of [lines] at [sizes] from the ring centre. */
  private fun farthest(lines: List<RingLine>, sizes: List<Float>): Float {
    val baselines = RingStack.baselines(lines, sizes, gap = 1f, measurer = FakeMeasurer)
    return lines.indices.maxOf { i ->
      val ink = FakeMeasurer.ink(lines[i].text, sizes[i], lines[i].weight)
      maxOf(hypot(ink.width / 2, baselines[i] + ink.top), hypot(ink.width / 2, baselines[i] + ink.bottom))
    }
  }

  private fun fitRing(lines: List<RingLine>, radius: Float) =
    TextFit.fitRing(lines, gap = 1f, innerRadius = radius, measurer = FakeMeasurer)

  @Test
  fun ring_roomy_keepsTheRequestedSizes() =
    assertEquals(listOf(8.5f, 28f, 10f), fitRing(ringLines(), radius = 60f))

  @Test
  fun ring_toleratesOneDpTouch() {
    val lines = ringLines()
    val atOne = farthest(lines, listOf(8.5f, 28f, 10f))
    assertEquals(listOf(8.5f, 28f, 10f), fitRing(lines, radius = atOne - 0.9f))
    assertTrue(fitRing(lines, radius = atOne - 1.5f) != listOf(8.5f, 28f, 10f))
  }

  /** Review finding 4: a long kWh / Ah line at 50 % shrinks alone; the SOC number keeps its size. */
  @Test
  fun wideUnderLine_shrinksAlone() {
    val lines = ringLines(under = "4,58 кВт·год")
    val sizes = fitRing(lines, radius = 31f)
    assertEquals(28f, sizes[1], 0f)
    assertEquals(8.5f, sizes[0], 0f)
    assertTrue("${sizes[2]}", sizes[2] < 10f)
    assertTrue(farthest(lines, sizes) <= 31f + TextFit.RING_TOLERANCE_DP)
  }

  /** Review finding 4: a raised SOC shrinks first, never making "SOC" or kWh smaller than at 50 %. */
  @Test
  fun raisedSoc_neverShrinksTheOtherLinesBelowFifty() {
    val at50 = fitRing(ringLines(), radius = 31f)
    val at100 = fitRing(ringLines(soc = 56f), radius = 31f)
    assertTrue("$at100 vs $at50", at100[0] >= at50[0] && at100[2] >= at50[2])
    assertTrue("$at100 vs $at50", at100[1] >= at50[1] && at100[1] <= 56f)
    assertTrue(farthest(ringLines(soc = 56f), at100) <= 31f + TextFit.RING_TOLERANCE_DP)
  }

  /**
   * Re-review finding 1: a kWh line too wide even at 50 % no longer pulls a raised SOC number back
   * to its 50 % size first. The number can grow only while the kWh line still fits its chord: the
   * stack is centred, so a larger number moves the kWh line down. Here (the fake's kWh line is at
   * its limit) it stays at 28; no line gets smaller than at 50 %.
   */
  @Test
  fun wideUnderLine_raisedSoc_keepsEveryLineAtLeastAtFifty() {
    val at50 = fitRing(ringLines(under = "4,58 кВт·год"), radius = 31f)
    val at100 = fitRing(ringLines(soc = 56f, under = "4,58 кВт·год"), radius = 31f)
    at50.indices.forEach { assertTrue("$at100 vs $at50", at100[it] >= at50[it]) }
    assertTrue(farthest(ringLines(soc = 56f, under = "4,58 кВт·год"), at100) <= 31f + TextFit.RING_TOLERANCE_DP)
  }

  /** A raised SOC number grows as far as the circle allows, in steps of its 50 % size. */
  @Test
  fun raisedSoc_growsWhileTheCircleHasRoom() {
    val sizes = fitRing(ringLines(soc = 56f), radius = 36f)
    assertTrue("$sizes", sizes[1] > 28f && sizes[1] <= 56f)
    assertEquals(8.5f, sizes[0], 0f)
    assertEquals(10f, sizes[2], 0f)
    assertTrue(farthest(ringLines(soc = 56f), sizes) <= 36f + TextFit.RING_TOLERANCE_DP)
  }

  /** Every line raised: none ends smaller than at 50 % (re-review finding 5). */
  @Test
  fun allRaised_noLineSmallerThanAtFifty() {
    for (under in listOf("4.58 kWh", "4,58 кВт·год")) {
      val at50 = fitRing(ringLines(under = under), radius = 31f)
      val at100 = fitRing(ringLines(soc = 56f, under = under, label = 17f, underSize = 20f), radius = 31f)
      at50.indices.forEach { assertTrue("$under $at100 vs $at50", at100[it] >= at50[it]) }
    }
  }

  /** A stack taller than the circle shrinks all lines by one factor (no width: only the height). */
  @Test
  fun tallStack_shrinksByOneFactor() {
    val lines =
      listOf(
        RingLine("", 20f, TextWeight.NORMAL, lineHeight = 1f),
        RingLine("b", 40f, TextWeight.BOLD, lineHeight = 1f),
        RingLine("", 20f, TextWeight.NORMAL, lineHeight = 1f),
      )
    val sizes = fitRing(lines, radius = 30f)
    assertTrue("$sizes", sizes[1] < 40f)
    assertEquals(sizes[0] / 20f, sizes[1] / 40f, 1e-3f)
    assertEquals(sizes[2] / 20f, sizes[1] / 40f, 1e-3f)
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
    val baseline = RingStack.baselines(lines, listOf(10f, 20f, 10f), gap = 2f, measurer = FakeMeasurer)[1]
    // Content area 1.17 × 20 centred in a 20 dp box: the baseline is 0.93 × 20 below its top.
    val contentTop = -(1.17f * 20f) / 2
    assertEquals(contentTop + 0.93f * 20f, baseline, 1e-3f)
  }
}
