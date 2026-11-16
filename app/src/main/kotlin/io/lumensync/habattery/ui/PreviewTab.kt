package io.lumensync.habattery.ui

import android.appwidget.AppWidgetManager
import android.os.Bundle
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import io.lumensync.habattery.widget.LayoutSpec
import kotlin.math.min

/**
 * A request to show [tab]. The activity makes a new one (higher [count]) when a widget tap names a
 * widget while the app is open; the card applies a request once per [count].
 */
data class PreviewRequest(val tab: PreviewTab, val count: Int = 0) {
  /** The request after an intent that names a widget's tab ([named]); none named keeps this one. */
  fun next(named: PreviewTab?): PreviewRequest = named?.let { PreviewRequest(it, count + 1) } ?: this

  /**
   * The card's tab once this request reaches a card that shows [current] and last saw
   * [seenCount]. Only a request the card has not seen, with a count above 0, switches the tab: a
   * new activity (rotation, language) starts at count 0, so a recreation keeps the user's own tab.
   */
  fun tabFor(current: PreviewTab, seenCount: Int): PreviewTab =
    if (count != seenCount && count > 0) tab else current
}

/**
 * The size Android reports in a widget's [options] for the current orientation (the widget options
 * give a range per orientation; portrait: min width × max height, landscape: max width × min
 * height). A missing key counts as 0, so a widget without a size yet is 0 × 0.
 */
fun reportedWidgetSize(options: Bundle, portrait: Boolean): DpSize {
  fun dp(key: String) = options.getInt(key).dp
  return if (portrait) {
    DpSize(dp(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH), dp(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT))
  } else {
    DpSize(dp(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH), dp(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT))
  }
}

/** The preview's size tabs (spec 0006, 5.2): each v5 layout with its home-screen cells. */
enum class PreviewTab(val layout: LayoutSpec, val columns: Int, val rows: Int) {
  TAB_4X2(LayoutSpec.L4x2, 4, 2),
  TAB_5X2(LayoutSpec.L5x2, 5, 2),
  TAB_4X3(LayoutSpec.L4x3, 4, 3),
  TAB_5X3(LayoutSpec.L5x3, 5, 3);

  companion object {
    /** The tab for a widget of [size] (0 × 0 while it has none → the smallest layout). */
    fun forSize(size: DpSize): PreviewTab {
      val layout = LayoutSpec.forSize(size.width.value, size.height.value)
      return entries.first { it.layout === layout }
    }

    /**
     * The tab of the widget the app was opened for (spec 0006, B1): [configured] (placement or
     * reconfigure) before [tapped] (the widget the user tapped). Each is the size Android reports
     * for it, 0 × 0 while it has none, or null when the intent names no such widget. Null when
     * neither is named.
     */
    fun named(configured: DpSize?, tapped: DpSize?): PreviewTab? = (configured ?: tapped)?.let(::forSize)

    /**
     * The tab the preview opens on (spec 0006, B1): the configured widget, else the tapped one,
     * else the first placed widget ([firstPlaced], same kind of size); none → 4×2. The first known
     * size picks the layout the home screen shows at it ([LayoutSpec.forSize]). The choice is not
     * stored.
     */
    fun first(configured: DpSize?, tapped: DpSize?, firstPlaced: DpSize?): PreviewTab =
      named(configured, tapped) ?: firstPlaced?.let(::forSize) ?: TAB_4X2

    /**
     * How much a preview [designWidth] dp wide shrinks to fit [availableWidth] dp, keeping its
     * shape; it never grows (spec 0006, 5.2).
     */
    fun scale(designWidth: Float, availableWidth: Float): Float = min(1f, availableWidth / designWidth)
  }
}
