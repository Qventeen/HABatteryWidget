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
  val smallText: TextUnit,
  val gap: Dp,
  val statusTop: Dp,
  val barTop: Dp,
  val barHeight: Dp,
  val chipPadH: Dp,
  val chipPadV: Dp,
  val chipCorner: Dp,
  val chipText: TextUnit,
  val sectionTop: Dp,
  val infoPadV: Dp,
  val badgeWidth: Dp,
  val badgeCorner: Dp,
  val badgeText: TextUnit,
  val cellText: TextUnit,
  val tilePadH: Dp,
  val tilePadV: Dp,
  val tileCorner: Dp,
  val tileBottom: Dp,
) {
  companion object {
    /** Below this width the widget uses tighter spacing. */
    val NARROW_WIDTH = 235.dp

    fun of(tier: WidgetDensityTier, narrow: Boolean): WidgetDimens {
      val compact = tier == WidgetDensityTier.COMPACT
      val large = tier == WidgetDensityTier.LARGE
      return WidgetDimens(
        padH = if (narrow) 8.dp else if (compact) 10.dp else 12.dp,
        padV =
          when (tier) {
            WidgetDensityTier.SUMMARY_ONLY -> 6.dp
            WidgetDensityTier.COMPACT -> 5.dp
            WidgetDensityTier.REGULAR -> 9.dp
            WidgetDensityTier.LARGE -> 11.dp
          },
        chargeText =
          when (tier) {
            WidgetDensityTier.SUMMARY_ONLY -> 24.sp
            WidgetDensityTier.COMPACT -> 22.sp
            WidgetDensityTier.REGULAR -> 30.sp
            WidgetDensityTier.LARGE -> 34.sp
          },
        metricsText =
          if (compact && narrow) 11.sp else if (compact) 12.sp else if (large) 13.5.sp else 13.sp,
        statusText = if (compact) 10.5.sp else if (large) 12.5.sp else 12.sp,
        smallText = if (compact) 9.5.sp else 11.sp,
        gap = if (compact || narrow) 6.dp else 10.dp,
        statusTop = if (compact) 1.dp else 4.dp,
        barTop = if (compact) 3.dp else if (large) 6.dp else 5.dp,
        barHeight = if (compact) 5.dp else if (large) 9.dp else 8.dp,
        chipPadH = if (compact || narrow) 6.dp else 8.dp,
        chipPadV = if (compact) 2.5.dp else 4.dp,
        chipCorner = if (compact) 8.dp else 10.dp,
        chipText = if (compact) 10.sp else 11.sp,
        sectionTop = if (compact) 3.dp else if (large) 8.dp else 6.dp,
        infoPadV = if (compact) 2.dp else 4.dp,
        badgeWidth = if (compact) 20.dp else 22.dp,
        badgeCorner = if (compact) 5.dp else 6.dp,
        badgeText = if (compact) 9.sp else 10.sp,
        cellText = if (compact) 10.5.sp else if (large) 12.5.sp else 12.sp,
        tilePadH = if (compact || narrow) 5.dp else 8.dp,
        tilePadV =
          when (tier) {
            WidgetDensityTier.SUMMARY_ONLY, WidgetDensityTier.COMPACT -> 1.dp
            WidgetDensityTier.REGULAR -> 2.5.dp
            WidgetDensityTier.LARGE -> 3.5.dp
          },
        tileCorner = if (compact) 7.dp else 8.dp,
        tileBottom = if (compact) 1.5.dp else 3.dp,
      )
    }
  }
}
