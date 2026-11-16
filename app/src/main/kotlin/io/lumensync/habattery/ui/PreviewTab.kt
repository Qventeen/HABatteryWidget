package io.lumensync.habattery.ui

import androidx.compose.ui.unit.DpSize
import io.lumensync.habattery.widget.LayoutSpec
import kotlin.math.min

/** The preview's size tabs (spec 0006, 5.2): each v5 layout with its home-screen cells. */
enum class PreviewTab(val layout: LayoutSpec, val columns: Int, val rows: Int) {
  TAB_4X2(LayoutSpec.L4x2, 4, 2),
  TAB_5X2(LayoutSpec.L5x2, 5, 2),
  TAB_4X3(LayoutSpec.L4x3, 4, 3),
  TAB_5X3(LayoutSpec.L5x3, 5, 3);

  companion object {
    /**
     * The tab the preview opens on (spec 0006, B1). [configured]: the size Android reports for the
     * widget the app was opened for (0 × 0 while it has none); [firstPlaced]: the same for the
     * first placed widget. The first known size picks the layout the home screen shows at it
     * ([LayoutSpec.forSize]); none → 4×2. The choice is not stored.
     */
    fun first(configured: DpSize?, firstPlaced: DpSize?): PreviewTab {
      val size = configured ?: firstPlaced ?: return TAB_4X2
      val layout = LayoutSpec.forSize(size.width.value, size.height.value)
      return entries.first { it.layout === layout }
    }

    /**
     * How much a preview [designWidth] dp wide shrinks to fit [availableWidth] dp, keeping its
     * shape; it never grows (spec 0006, 5.2).
     */
    fun scale(designWidth: Float, availableWidth: Float): Float = min(1f, availableWidth / designWidth)
  }
}
