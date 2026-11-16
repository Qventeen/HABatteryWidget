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
 * box. Text width is not exactly linear in the size (hinting), so the proportional guess is checked
 * and stepped down until it fits.
 */
object TextFit {
  /** Below 1 dp of ink outside the ring's inner circle is accepted (spec 0005, 5.5). */
  const val RING_TOLERANCE_DP = 1f

  /** Room kept free in a box's height and width: views are laid out in whole pixels. */
  const val HEIGHT_SLACK_DP = 0.5f
  const val WIDTH_SLACK_DP = 0.5f

  private const val STEP_DP = 0.25f
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
    val w = width(requested)
    val h = height(requested)
    var size = requested * minOf(if (w > 0f) roomW / w else 1f, if (h > 0f) roomH / h else 1f)
    while (size > STEP_DP && !fits(size)) size -= STEP_DP
    return size.coerceAtLeast(0f)
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
   * The shown sizes of the ring lines (review of spec 0005, finding 4). Each line's ink must stay
   * inside the inner circle of [innerRadius] (its width within the chord at its height), give or
   * take [RING_TOLERANCE_DP]:
   * 1. Raised lines (asking for more than 50 %) shrink first, at most down to their 50 % size.
   * 2. A stack taller than the circle shrinks all lines by one factor.
   * 3. A line still too wide for its chord shrinks alone.
   * So a long kWh / Ah line does not make the SOC number smaller, and a large SOC number never makes
   * the other lines smaller than they are at 50 %.
   */
  fun fitRing(lines: List<RingLine>, gap: Float, innerRadius: Float, measurer: TextMeasurer): List<Float> {
    val sizes = lines.map { it.requested }.toMutableList()
    val limit = innerRadius + RING_TOLERANCE_DP

    fun ink(i: Int, baseline: Float): Pair<Float, Float> {
      val ink = measurer.ink(lines[i].text, sizes[i], lines[i].weight)
      return ink.width / 2 to maxOf(abs(baseline + ink.top), abs(baseline + ink.bottom))
    }
    fun tooWide(): List<Int> {
      val baselines = RingStack.baselines(lines, sizes, gap, measurer)
      return lines.indices.filter { i -> ink(i, baselines[i]).let { (half, y) -> hypot(half, y) > limit } }
    }
    fun tooTall(): Boolean {
      val baselines = RingStack.baselines(lines, sizes, gap, measurer)
      return lines.indices.any { i -> ink(i, baselines[i]).second > limit }
    }

    var steps = 0
    while (steps++ < MAX_STEPS && tooWide().isNotEmpty()) {
      val raised = lines.indices.filter { sizes[it] > lines[it].floor + EPSILON }
      if (raised.isEmpty()) break
      raised.forEach { sizes[it] = maxOf(lines[it].floor, sizes[it] - lines[it].requested * FACTOR_STEP) }
    }
    while (steps++ < MAX_STEPS && tooTall()) {
      for (i in sizes.indices) sizes[i] *= 1 - FACTOR_STEP
    }
    while (steps++ < MAX_STEPS) {
      val wide = tooWide()
      if (wide.isEmpty()) break
      wide.forEach { sizes[it] = maxOf(STEP_DP, sizes[it] - lines[it].requested * FACTOR_STEP) }
    }
    return sizes
  }

  private const val MAX_STEPS = 1000
}
