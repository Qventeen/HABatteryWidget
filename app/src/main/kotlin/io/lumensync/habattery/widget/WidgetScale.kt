package io.lumensync.habattery.widget

import kotlin.math.min

/**
 * One layout for every size (spec 0003, approach C1): fixed sizes are multiplied by
 * `min(1, width / 360 dp, height / 180 dp)`. Bigger than the design, they stay at 1×.
 */
object WidgetScale {
  const val WIDTH_DP = 360f
  const val HEIGHT_DP = 180f

  fun of(widthDp: Float, heightDp: Float = HEIGHT_DP): Float =
    min(1f, min(widthDp / WIDTH_DP, heightDp / HEIGHT_DP))
}
