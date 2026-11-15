package io.lumensync.habattery.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import kotlin.math.roundToInt

/**
 * The filled part of the SOC ring. Glance has no ring view, so it is an alpha-only bitmap, tinted
 * where it is drawn; the track under it is the `ring_track` vector.
 */
internal object RingBitmap {
  private const val CACHE_SIZE = 4

  /** At 0 % a dot stays, as in the design (a round cap on an almost empty arc). */
  private const val MIN_SWEEP = 0.01f

  private data class Key(val percent: Int, val sizePx: Int, val strokePx: Float)

  private val cache =
    object : LinkedHashMap<Key, Bitmap>(CACHE_SIZE, 0.75f, true) {
      override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Key, Bitmap>) =
        size > CACHE_SIZE
    }

  /** An arc from 12 o'clock, clockwise, over [fraction] of the circle; cached per whole percent. */
  @Synchronized
  fun arc(fraction: Float, sizePx: Int, strokePx: Float): Bitmap {
    val percent = (fraction.coerceIn(0f, 1f) * 100).roundToInt()
    return cache.getOrPut(Key(percent, sizePx, strokePx)) { draw(percent, sizePx, strokePx) }
  }

  private fun draw(percent: Int, sizePx: Int, strokePx: Float): Bitmap {
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ALPHA_8)
    val paint =
      Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = strokePx
        strokeCap = Paint.Cap.ROUND
      }
    val inset = strokePx / 2
    val bounds = RectF(inset, inset, sizePx - inset, sizePx - inset)
    Canvas(bitmap).drawArc(bounds, -90f, maxOf(MIN_SWEEP, percent * 3.6f), false, paint)
    return bitmap
  }
}
