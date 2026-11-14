package io.lumensync.habattery.widget

import kotlin.math.max

enum class WidgetDensityTier {
  SUMMARY_ONLY,
  COMPACT,
  REGULAR,
  LARGE,
}

data class WidgetLayoutSpec(
  val tier: WidgetDensityTier,
  val showCells: Boolean,
  val cellRows: Int,
  val useRowSpacers: Boolean,
) {
  companion object {
    fun fromSize(heightDp: Float, cellCount: Int): WidgetLayoutSpec {
      val cellRows = (cellCount + 1) / 2
      if (cellCount <= 0) {
        val tier = when {
          heightDp < 135f -> WidgetDensityTier.SUMMARY_ONLY
          heightDp < 215f -> WidgetDensityTier.COMPACT
          else -> WidgetDensityTier.REGULAR
        }
        return WidgetLayoutSpec(
          tier = tier,
          showCells = false,
          cellRows = 0,
          useRowSpacers = false,
        )
      }

      val minCompactHeight = max(125f, 84f + cellRows * 15f)
      if (heightDp < minCompactHeight) {
        return WidgetLayoutSpec(
          tier = WidgetDensityTier.SUMMARY_ONLY,
          showCells = false,
          cellRows = cellRows,
          useRowSpacers = false,
        )
      }

      val minRegularHeight = max(215f, 134f + cellRows * 23f)
      val minLargeHeight = max(275f, 152f + cellRows * 26f)
      val tier = when {
        heightDp >= minLargeHeight -> WidgetDensityTier.LARGE
        heightDp >= minRegularHeight -> WidgetDensityTier.REGULAR
        else -> WidgetDensityTier.COMPACT
      }
      return WidgetLayoutSpec(
        tier = tier,
        showCells = true,
        cellRows = cellRows,
        useRowSpacers = cellRows in 2..5,
      )
    }
  }
}
