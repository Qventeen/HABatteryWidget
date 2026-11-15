package io.lumensync.habattery.widget

import android.content.Context
import android.graphics.Paint
import android.graphics.Rect
import android.text.TextPaint
import android.text.style.TextAppearanceSpan

/**
 * Measures text with the typeface Glance draws with: the same text appearance styles Glance puts on
 * a widget's text (the device default font family at weight 400 / 500 / 700), so the fit is made
 * for the font the home screen shows (spec 0005, 5.5). Runs in the app process; sizes are dp.
 */
class PaintTextMeasurer(context: Context) : TextMeasurer {
  private val density = context.resources.displayMetrics.density
  private val paints =
    TextWeight.entries.associateWith { weight ->
      TextPaint(Paint.ANTI_ALIAS_FLAG).also { TextAppearanceSpan(context, style(weight)).updateMeasureState(it) }
    }
  private val bounds = Rect()

  @Synchronized
  override fun width(text: String, sizeDp: Float, weight: TextWeight): Float =
    paint(sizeDp, weight).measureText(text) / density

  @Synchronized
  override fun metrics(sizeDp: Float, weight: TextWeight): LineMetrics {
    val m = paint(sizeDp, weight).fontMetrics
    return LineMetrics(m.ascent / density, m.descent / density, m.top / density, m.bottom / density)
  }

  @Synchronized
  override fun ink(text: String, sizeDp: Float, weight: TextWeight): Ink {
    val paint = paint(sizeDp, weight)
    paint.getTextBounds(text, 0, text.length, bounds)
    return Ink(paint.measureText(text) / density, bounds.top / density, bounds.bottom / density)
  }

  private fun paint(sizeDp: Float, weight: TextWeight): TextPaint =
    paints.getValue(weight).apply { textSize = sizeDp * density }

  private fun style(weight: TextWeight): Int =
    when (weight) {
      TextWeight.NORMAL -> androidx.glance.appwidget.R.style.Glance_AppWidget_TextAppearance_Normal
      TextWeight.MEDIUM -> androidx.glance.appwidget.R.style.Glance_AppWidget_TextAppearance_Medium
      TextWeight.BOLD -> androidx.glance.appwidget.R.style.Glance_AppWidget_TextAppearance_Bold
    }
}
