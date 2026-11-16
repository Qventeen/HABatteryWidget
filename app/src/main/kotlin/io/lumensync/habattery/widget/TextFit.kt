package io.lumensync.habattery.widget

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
 * size; null is CSS "normal" (the font's content height).
 */
data class RingLine(val text: String, val requested: Float, val weight: TextWeight, val lineHeight: Float?)

/**
 * The ring's three lines as the canvas stacks them: line boxes with [gap] between them, the whole
 * stack centred on the ring, each baseline where CSS puts it (half-leading above the content area).
 */
object RingStack {
  /** Baselines in dp from the ring centre (down is positive), with every size × [factor]. */
  fun baselines(lines: List<RingLine>, factor: Float, gap: Float, measurer: TextMeasurer): List<Float> {
    val metrics = lines.map { measurer.metrics(it.text, it.requested * factor, it.weight) }
    val heights = lines.mapIndexed { i, line -> lineBox(line, factor, metrics[i]) }
    var top = -(heights.sum() + gap * (lines.size - 1)) / 2
    return lines.indices.map { i ->
      val m = metrics[i]
      val baseline = top + (heights[i] - m.height) / 2 - m.ascent
      top += heights[i] + gap
      baseline
    }
  }

  private fun lineBox(line: RingLine, factor: Float, m: LineMetrics): Float =
    line.lineHeight?.let { it * line.requested * factor } ?: m.height
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
   * One factor ≤ 1 for all three ring lines (spec 0005, 5.5): with every size × factor, the ink of
   * each line stays inside the ring's inner circle of [innerRadius], give or take
   * [RING_TOLERANCE_DP].
   */
  fun fitRing(lines: List<RingLine>, gap: Float, innerRadius: Float, measurer: TextMeasurer): Float {
    fun fits(factor: Float): Boolean {
      val baselines = RingStack.baselines(lines, factor, gap, measurer)
      return lines.indices.all { i ->
        val line = lines[i]
        val ink = measurer.ink(line.text, line.requested * factor, line.weight)
        val far = maxOf(hypot(ink.width / 2, baselines[i] + ink.top), hypot(ink.width / 2, baselines[i] + ink.bottom))
        far <= innerRadius + RING_TOLERANCE_DP
      }
    }

    var factor = 1f
    while (factor > FACTOR_STEP && !fits(factor)) factor -= FACTOR_STEP
    return factor
  }
}
