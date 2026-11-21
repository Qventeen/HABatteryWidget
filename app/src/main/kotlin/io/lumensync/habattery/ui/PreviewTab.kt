package io.lumensync.habattery.ui

import android.appwidget.AppWidgetManager
import android.os.Bundle
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import io.lumensync.habattery.widget.BatteryWidgetReceiver
import io.lumensync.habattery.widget.InverterLayoutSpec
import io.lumensync.habattery.widget.InverterWidgetReceiver
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

/** [PreviewRequest] for the inverter strip card: the size [spec] to show, applied once per [count]. */
data class StripRequest(val spec: InverterLayoutSpec, val count: Int = 0) {
  /** The request after an intent that names a strip's size ([named]); none named keeps this one. */
  fun next(named: InverterLayoutSpec?): StripRequest = named?.let { StripRequest(it, count + 1) } ?: this

  /** The card's size once this request reaches a card that shows [current]; see [PreviewRequest.tabFor]. */
  fun specFor(current: InverterLayoutSpec, seenCount: Int): InverterLayoutSpec =
    if (count != seenCount && count > 0) spec else current

  companion object {
    /** The strip size of the widget the app was opened for ([opened]); null unless it is a strip. */
    fun named(opened: NamedWidget?): InverterLayoutSpec? =
      opened
        ?.takeIf { it.provider == InverterWidgetReceiver::class.java.name }
        ?.size
        ?.let { InverterLayoutSpec.forSize(it.width.value, it.height.value) }

    /**
     * The size the strip preview opens on, like [PreviewTab.first]: the strip the app was opened for
     * ([opened]), else the first placed strip ([firstPlaced], its reported size); none → 3×1.
     */
    fun first(opened: NamedWidget?, firstPlaced: DpSize?): InverterLayoutSpec =
      named(opened)
        ?: firstPlaced?.let { InverterLayoutSpec.forSize(it.width.value, it.height.value) }
        ?: InverterLayoutSpec.S3X1
  }
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

/**
 * The widget an intent names (placed, reconfigured or tapped): its provider class name (null when
 * Android no longer knows the widget) and the size Android reports for it, 0 × 0 while it has none.
 */
data class NamedWidget(val provider: String?, val size: DpSize)

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
     * The tab of the widget the app was opened for ([opened], or null when the intent names none).
     * Only a battery widget names one: a strip's one-row size is no battery layout (spec 0012).
     */
    fun named(opened: NamedWidget?): PreviewTab? =
      opened?.takeIf { it.provider == BatteryWidgetReceiver::class.java.name }?.size?.let(::forSize)

    /**
     * The tab the preview opens on (spec 0006): the battery widget the app was opened for, else the
     * first placed battery widget ([firstPlaced], its reported size); none → 4×2. The first known
     * size picks the layout the home screen shows at it ([LayoutSpec.forSize]). The choice is not
     * stored.
     */
    fun first(opened: NamedWidget?, firstPlaced: DpSize?): PreviewTab =
      named(opened) ?: firstPlaced?.let(::forSize) ?: TAB_4X2

    /**
     * How much a preview [designWidth] dp wide shrinks to fit [availableWidth] dp, keeping its
     * shape; it never grows (spec 0006, 5.2).
     */
    fun scale(designWidth: Float, availableWidth: Float): Float = min(1f, availableWidth / designWidth)
  }
}
