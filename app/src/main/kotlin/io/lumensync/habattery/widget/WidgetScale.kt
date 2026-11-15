package io.lumensync.habattery.widget

import kotlin.math.min

/**
 * One layout for every size (spec 0003, approach C1): fixed sizes are multiplied by `s`. The top row
 * keeps its 44 dp (the buttons' touch area), so the height scale uses the 136 dp below it:
 * `s = min(1, width / 360, (height − 44) / 136)`. Then the 104 dp ring always fits the body.
 * Bigger than the design, sizes stay at 1×.
 */
object WidgetScale {
  const val WIDTH_DP = 360f
  const val HEIGHT_DP = 180f
  const val TOP_ROW_DP = 44f

  fun of(widthDp: Float, heightDp: Float = HEIGHT_DP): Float =
    min(1f, min(widthDp / WIDTH_DP, (heightDp - TOP_ROW_DP) / (HEIGHT_DP - TOP_ROW_DP)))

  /** The card height (dp) at scale [s]: the top row stays 44 dp, everything else scales. */
  fun heightDp(s: Float): Float = TOP_ROW_DP + (HEIGHT_DP - TOP_ROW_DP) * s
}
