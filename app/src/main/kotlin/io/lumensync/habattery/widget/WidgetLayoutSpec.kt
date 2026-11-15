package io.lumensync.habattery.widget

enum class WidgetDensityTier {
  SUMMARY_ONLY,
  COMPACT,
  REGULAR,
}

data class WidgetLayoutSpec(val tier: WidgetDensityTier) {
  companion object {
    fun fromSize(heightDp: Float): WidgetLayoutSpec =
      WidgetLayoutSpec(
        when {
          heightDp < 135f -> WidgetDensityTier.SUMMARY_ONLY
          heightDp < 215f -> WidgetDensityTier.COMPACT
          else -> WidgetDensityTier.REGULAR
        }
      )
  }
}
