package io.lumensync.habattery.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.core.graphics.createBitmap
import kotlin.math.roundToInt

/**
 * The SOC ring. Glance has no ring view, so the track and the filled arc are alpha-only bitmaps,
 * tinted where they are drawn. Each layout has its own ring size and stroke (design v5).
 */
internal object RingBitmap {
  /** Track and arc for each of the four layouts. */
  private const val CACHE_SIZE = 8

  /** At 0 % a dot stays, as in the design (a round cap on an almost empty arc). */
  private const val MIN_SWEEP = 0.01f

  /** [percent] is null for the track. */
  private data class Key(val percent: Int?, val sizePx: Int, val strokePx: Float)

  private val cache =
    object : LinkedHashMap<Key, Bitmap>(CACHE_SIZE, 0.75f, true) {
      override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Key, Bitmap>) =
        size > CACHE_SIZE
    }

  /** The full circle under the arc. */
  @Synchronized
  fun track(sizePx: Int, strokePx: Float): Bitmap =
    cache.getOrPut(Key(null, sizePx, strokePx)) { draw(null, sizePx, strokePx) }

  /** An arc from 12 o'clock, clockwise, over [fraction] of the circle; cached per whole percent. */
  @Synchronized
  fun arc(fraction: Float, sizePx: Int, strokePx: Float): Bitmap {
    val percent = (fraction.coerceIn(0f, 1f) * 100).roundToInt()
    return cache.getOrPut(Key(percent, sizePx, strokePx)) { draw(percent, sizePx, strokePx) }
  }

  private fun draw(percent: Int?, sizePx: Int, strokePx: Float): Bitmap {
    val bitmap = createBitmap(sizePx, sizePx, Bitmap.Config.ALPHA_8)
    val paint =
      Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = strokePx
        strokeCap = Paint.Cap.ROUND
      }
    val inset = strokePx / 2
    val bounds = RectF(inset, inset, sizePx - inset, sizePx - inset)
    val sweep = percent?.let { maxOf(MIN_SWEEP, it * 3.6f) } ?: 360f
    Canvas(bitmap).drawArc(bounds, -90f, sweep, false, paint)
    return bitmap
  }
}
