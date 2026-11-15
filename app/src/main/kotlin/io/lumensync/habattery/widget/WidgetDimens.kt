package io.lumensync.habattery.widget

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Spacing and font sizes per density tier, kept from the old widget's look. */
internal data class WidgetDimens(
  val padH: Dp,
  val padV: Dp,
  val chargeText: TextUnit,
  val metricsText: TextUnit,
  val statusText: TextUnit,
  /** The status line may wrap, so a long translation keeps the time left visible. */
  val statusLines: Int,
  val smallText: TextUnit,
  val gap: Dp,
  val statusTop: Dp,
  val barTop: Dp,
  val barHeight: Dp,
  val chipPadH: Dp,
  val chipPadV: Dp,
  val chipCorner: Dp,
  val chipText: TextUnit,
  val infoPadV: Dp,
) {
  companion object {
    /** Below this width the widget uses tighter spacing. */
    val NARROW_WIDTH = 235.dp

    fun of(tier: WidgetDensityTier, narrow: Boolean): WidgetDimens {
      val compact = tier == WidgetDensityTier.COMPACT
      return WidgetDimens(
        padH = if (narrow) 8.dp else if (compact) 10.dp else 12.dp,
        padV =
          when (tier) {
            WidgetDensityTier.SUMMARY_ONLY -> 6.dp
            WidgetDensityTier.COMPACT -> 5.dp
            WidgetDensityTier.REGULAR -> 9.dp
          },
        chargeText =
          when (tier) {
            WidgetDensityTier.SUMMARY_ONLY -> 24.sp
            WidgetDensityTier.COMPACT -> 22.sp
            WidgetDensityTier.REGULAR -> 30.sp
          },
        metricsText =
          if (compact && narrow) 11.sp else if (compact) 12.sp else 13.sp,
        statusText = if (compact) 10.5.sp else 12.sp,
        statusLines = if (tier == WidgetDensityTier.SUMMARY_ONLY) 1 else 2,
        smallText = if (compact) 9.5.sp else 11.sp,
        gap = if (compact || narrow) 6.dp else 10.dp,
        statusTop = if (compact) 1.dp else 4.dp,
        barTop = if (compact) 3.dp else 5.dp,
        barHeight = if (compact) 5.dp else 8.dp,
        chipPadH = if (compact || narrow) 6.dp else 8.dp,
        chipPadV = if (compact) 2.5.dp else 4.dp,
        chipCorner = if (compact) 8.dp else 10.dp,
        chipText = if (compact) 10.sp else 11.sp,
        infoPadV = if (compact) 2.dp else 4.dp,
      )
    }
  }
}
