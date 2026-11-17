package io.lumensync.habattery.widget

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Paint
import android.graphics.Rect
import android.os.Build
import android.text.TextPaint
import android.text.style.TextAppearanceSpan

/**
 * Measures text with the typeface Glance draws with: the same text appearance styles Glance puts on
 * a widget's text (the device default font family at weight 400 / 500 / 700), so the fit is made
 * for the font the home screen shows (spec 0005, 5.5). Runs in the app process; sizes are dp.
 */
class PaintTextMeasurer(context: Context) : TextMeasurer {
  /** Not `density`: inside `TextPaint.apply` that name is the paint's own field. */
  private val pxPerDp = context.resources.displayMetrics.density
  private val paints =
    TextWeight.entries.associateWith { weight ->
      TextPaint(Paint.ANTI_ALIAS_FLAG).also { TextAppearanceSpan(context, style(weight)).updateMeasureState(it) }
    }
  private val bounds = Rect()
  private val metricsInt = Paint.FontMetricsInt()

  @Synchronized
  override fun width(text: String, sizeDp: Float, weight: TextWeight): Float =
    paint(sizeDp, weight).measureText(text) / pxPerDp

  /**
   * On Android 13+ the metrics of every font that draws [text] (a fallback font for Δ, ·, ° or
   * Cyrillic can be taller), as the text view uses them; before that (the call is new in 13), the
   * main font's.
   */
  @Synchronized
  override fun metrics(text: String, sizeDp: Float, weight: TextWeight): LineMetrics {
    val paint = paint(sizeDp, weight)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && text.isNotEmpty()) {
      paint.getFontMetricsInt(text, 0, text.length, 0, text.length, false, metricsInt)
      return LineMetrics(
        metricsInt.ascent / pxPerDp,
        metricsInt.descent / pxPerDp,
        metricsInt.top / pxPerDp,
        metricsInt.bottom / pxPerDp,
      )
    }
    val m = paint.fontMetrics
    return LineMetrics(m.ascent / pxPerDp, m.descent / pxPerDp, m.top / pxPerDp, m.bottom / pxPerDp)
  }

  @Synchronized
  override fun ink(text: String, sizeDp: Float, weight: TextWeight): Ink {
    val paint = paint(sizeDp, weight)
    paint.getTextBounds(text, 0, text.length, bounds)
    return Ink(paint.measureText(text) / pxPerDp, bounds.top / pxPerDp, bounds.bottom / pxPerDp)
  }

  private fun paint(sizeDp: Float, weight: TextWeight): TextPaint =
    paints.getValue(weight).apply { textSize = sizeDp * pxPerDp }

  /** Glance's own styles on purpose: the measure must use what Glance draws with. */
  @SuppressLint("PrivateResource")
  private fun style(weight: TextWeight): Int =
    when (weight) {
      TextWeight.NORMAL -> androidx.glance.appwidget.R.style.Glance_AppWidget_TextAppearance_Normal
      TextWeight.MEDIUM -> androidx.glance.appwidget.R.style.Glance_AppWidget_TextAppearance_Medium
      TextWeight.BOLD -> androidx.glance.appwidget.R.style.Glance_AppWidget_TextAppearance_Bold
    }
}
