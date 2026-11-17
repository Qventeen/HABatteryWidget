package io.lumensync.habattery.widget

/**
 * Places the ring's three lines by their visible ink (spec 0005, D; owner 2026-11-16): each
 * baseline where [RingStack] puts it, with a fixed visible gap between the lines. An Android text
 * view is taller than its glyphs (font padding: from the font's top to its bottom, baseline at
 * −top), so each view's centre is moved to put its baseline there.
 */
internal object RingLayout {
  /** The centre of each line's text view, in dp from the ring centre (down is positive). */
  fun centers(lines: List<RingLine>, gap: Float, measurer: TextMeasurer): List<Float> {
    val baselines = RingStack.baselines(lines, lines.map { it.requested }, gap, measurer)
    return lines.mapIndexed { i, line ->
      val m = measurer.metrics(line.text, line.requested, line.weight)
      baselines[i] + (m.top + m.bottom) / 2
    }
  }
}
