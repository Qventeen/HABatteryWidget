package io.lumensync.habattery.widget

import kotlin.math.abs
import kotlin.math.hypot

/** The font weights the widget draws with. */
enum class TextWeight {
  NORMAL,
  MEDIUM,
  BOLD,
}

/** Font metrics of one line in dp, as Android gives them: [ascent] and [top] are negative. */
data class LineMetrics(val ascent: Float, val descent: Float, val top: Float, val bottom: Float) {
  /** The content height: what CSS calls a "normal" line and what one line of glyphs needs. */
  val height: Float
    get() = descent - ascent
}

/** The ink of a text in dp: its [width], and [top] / [bottom] relative to the baseline. */
data class Ink(val width: Float, val top: Float, val bottom: Float)

/** Measures text as the widget draws it (font, weight). Sizes are dp. */
interface TextMeasurer {
  /** The advance width of [text]. */
  fun width(text: String, sizeDp: Float, weight: TextWeight): Float

  /** The line's metrics with [text]: fonts that draw some of its glyphs (fallback) count too. */
  fun metrics(text: String, sizeDp: Float, weight: TextWeight): LineMetrics

  fun ink(text: String, sizeDp: Float, weight: TextWeight): Ink
}

/** The inner size of a text's box in dp. */
data class Box(val width: Float, val height: Float)

/**
 * One line of the ring's text stack. [lineHeight] is the canvas CSS line height as a factor of the
 * size; null is CSS "normal" (the font's content height). [floor] is the line's size at 50 %: a
 * line asking for more is raised, and shrinks first when the ring is too small (review, 0005).
 */
data class RingLine(
  val text: String,
  val requested: Float,
  val weight: TextWeight,
  val lineHeight: Float?,
  val floor: Float = requested,
)

/**
 * The ring's three lines as the canvas stacks them: line boxes with [gap] between them, the whole
 * stack centred on the ring, each baseline where CSS puts it (half-leading above the content area).
 */
object RingStack {
  /** Baselines in dp from the ring centre (down is positive), with the lines at [sizes]. */
  fun baselines(lines: List<RingLine>, sizes: List<Float>, gap: Float, measurer: TextMeasurer): List<Float> {
    val metrics = lines.mapIndexed { i, line -> measurer.metrics(line.text, sizes[i], line.weight) }
    val heights = lines.mapIndexed { i, line -> line.lineHeight?.let { it * sizes[i] } ?: metrics[i].height }
    var top = -(heights.sum() + gap * (lines.size - 1)) / 2
    return lines.indices.map { i ->
      val m = metrics[i]
      val baseline = top + (heights[i] - m.height) / 2 - m.ascent
      top += heights[i] + gap
      baseline
    }
  }
}

/**
 * Spec 0005, approach C1: a text is shown at its requested size, or smaller when it does not fit its
 * box.
 */
object TextFit {
  /** Below 1 dp of ink outside the ring's inner circle is accepted (spec 0005, 5.5). */
  const val RING_TOLERANCE_DP = 1f

  /** Room kept free in a box's height and width: views are laid out in whole pixels. */
  const val HEIGHT_SLACK_DP = 0.5f
  const val WIDTH_SLACK_DP = 0.5f

  private const val STEP_DP = 0.25f
  private const val SEARCH_STEPS = 24
  private const val FACTOR_STEP = 0.01f
  private const val EPSILON = 0.01f

  /**
   * The largest size ≤ [requested] at which [texts] side by side fit [box]: the sum of their widths
   * within its width, and their ink within its height. One size for all of them.
   */
  fun fit(texts: List<String>, requested: Float, weight: TextWeight, box: Box, measurer: TextMeasurer): Float {
    fun width(size: Float) = texts.sumOf { measurer.width(it, size, weight).toDouble() }.toFloat()
    fun height(size: Float) = texts.maxOf { inkHeight(it, size, weight, measurer) }
    val roomW = box.width - WIDTH_SLACK_DP
    val roomH = box.height - HEIGHT_SLACK_DP
    fun fits(size: Float) = width(size) <= roomW + EPSILON && height(size) <= roomH + EPSILON

    if (requested <= 0f || fits(requested)) return requested.coerceAtLeast(0f)
    // The largest size that fits, found the same way whatever was asked for, so a larger request
    // never shows smaller (text width is not exactly linear in the size).
    var fitting = 0f
    var tooBig = requested
    repeat(SEARCH_STEPS) {
      val mid = (fitting + tooBig) / 2
      if (fits(mid)) fitting = mid else tooBig = mid
    }
    return fitting
  }

  /**
   * The height a one-line text view needs so that no ink is cut. Android draws the line from the
   * font's top (font padding). In a box lower than the whole line the view is cut to the box and
   * keeps its top, so the box must hold the font's top and the ink below the baseline (a decimal
   * comma reaches below it); in a higher box the view is centred and fits anyway.
   */
  private fun inkHeight(text: String, size: Float, weight: TextWeight, measurer: TextMeasurer): Float =
    -measurer.metrics(text, size, weight).top + maxOf(measurer.ink(text, size, weight).bottom, 0f)

  /**
   * The shown sizes of the ring lines (review of spec 0005). Each line's ink must stay inside the
   * inner circle of [innerRadius] (its width within the circle's chord at its height), give or take
   * [RING_TOLERANCE_DP]:
   * 1. The 50 % result: every line at the smaller of its request and its 50 % size; a stack taller
   *    than the circle shrinks all lines by one factor, then a line still too wide for its chord
   *    shrinks alone.
   * 2. Raised lines (asking for more than 50 %) grow from their 50 % result toward their request,
   *    in steps of their 50 % size; a line stops at the last step where every line still fits.
   *    The other lines keep their 50 % result.
   * So no line is ever smaller than at 50 %, a long kWh / Ah line does not make the SOC number
   * smaller, and a raised SOC number grows as far as the circle allows.
   */
  fun fitRing(lines: List<RingLine>, gap: Float, innerRadius: Float, measurer: TextMeasurer): List<Float> {
    val limit = innerRadius + RING_TOLERANCE_DP
    val sizes = lines.map { minOf(it.requested, it.floor) }.toMutableList()
    val base = sizes.toList()

    fun inkOf(i: Int, baseline: Float): Pair<Float, Float> {
      val ink = measurer.ink(lines[i].text, sizes[i], lines[i].weight)
      return ink.width / 2 to maxOf(abs(baseline + ink.top), abs(baseline + ink.bottom))
    }
    fun tooWide(): List<Int> {
      val baselines = RingStack.baselines(lines, sizes, gap, measurer)
      return lines.indices.filter { i -> inkOf(i, baselines[i]).let { (half, y) -> hypot(half, y) > limit } }
    }
    fun tooTall(): Boolean {
      val baselines = RingStack.baselines(lines, sizes, gap, measurer)
      return lines.indices.any { i -> inkOf(i, baselines[i]).second > limit }
    }

    var steps = 0
    while (steps++ < MAX_STEPS && tooTall()) {
      for (i in sizes.indices) sizes[i] *= 1 - FACTOR_STEP
    }
    while (steps++ < MAX_STEPS) {
      val wide = tooWide()
      if (wide.isEmpty()) break
      wide.forEach { sizes[it] = maxOf(STEP_DP, sizes[it] - base[it] * FACTOR_STEP) }
    }

    val growing = lines.indices.filter { lines[it].requested > lines[it].floor + EPSILON }.toMutableList()
    while (growing.isNotEmpty() && steps++ < MAX_STEPS) {
      for (i in growing.toList()) {
        val before = sizes[i]
        sizes[i] = minOf(lines[i].requested, before + lines[i].floor * FACTOR_STEP)
        if (tooWide().isNotEmpty()) {
          sizes[i] = before
          growing -= i
        } else if (sizes[i] >= lines[i].requested - EPSILON) {
          growing -= i
        }
      }
    }
    return sizes
  }

  private const val MAX_STEPS = 1000
}
