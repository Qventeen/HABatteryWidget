package io.lumensync.habattery.widget

import android.content.Context
import android.content.res.Configuration
import android.graphics.RectF
import android.text.Layout
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.compose.runtime.Composable
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import java.util.Locale

/** A widget that draws [content] at the exact size it is given. */
internal class FitWidget(private val content: @Composable () -> Unit) : GlanceAppWidget() {
  override val sizeMode: SizeMode = SizeMode.Exact

  override suspend fun provideGlance(context: Context, id: GlanceId) = provideContent { content() }
}

internal fun descriptions(view: View): List<String> =
  listOfNotNull(view.contentDescription?.toString()) +
    if (view is ViewGroup) (0 until view.childCount).flatMap { descriptions(view.getChildAt(it)) } else emptyList()

internal fun fitContext(base: Context, lang: String, fontScale: Float): Context {
  val config = Configuration(base.resources.configuration)
  config.setLocale(Locale.forLanguageTag(lang))
  config.fontScale = fontScale
  return base.createConfigurationContext(config)
}

/**
 * Every text that does not show whole: wrapped, ending with "…" (unless [mayEllipsize] allows that
 * text), too narrow, or cut by a view around it.
 */
internal fun clipped(root: View, mayEllipsize: (String) -> Boolean = { false }): List<String> {
  val found = mutableListOf<String>()
  fun visit(view: View, ancestors: List<View>) {
    if (view.visibility != View.VISIBLE) return
    if (view is TextView && view.text.isNotEmpty()) {
      val text = view.text.toString()
      val layout = view.layout
      val needed = Layout.getDesiredWidth(view.text, view.paint)
      val room = view.width - view.totalPaddingLeft - view.totalPaddingRight
      if (layout == null || layout.getLineEnd(0) < text.length) found += "'$text' wraps"
      else if (layout.getEllipsisCount(0) > 0) { if (!mayEllipsize(text)) found += "'$text' is cut to '…'" }
      else if (needed > room + 0.5f) found += "'$text' needs $needed px, has $room"
      else {
        val box = contentBox(view, root)
        ancestors.forEach { a ->
          val clip = rectIn(a, root).apply {
            if (a is ViewGroup && a.clipToPadding) {
              left += a.paddingLeft; top += a.paddingTop; right -= a.paddingRight; bottom -= a.paddingBottom
            }
          }
          // Views are laid out in whole pixels: 1 px of rounding is not a cut.
          if (box.left < clip.left - 1f || box.right > clip.right + 1f || box.top < clip.top - 1f || box.bottom > clip.bottom + 1f) {
            found += "'$text' cut: $box outside $clip"
            return
          }
        }
      }
    }
    if (view is ViewGroup) for (i in 0 until view.childCount) visit(view.getChildAt(i), ancestors + view)
  }
  visit(root, emptyList())
  return found
}

/**
 * The ink of the line: from the line's left to its right, from the glyphs' top to their bottom
 * (the font's padding and an empty descent may be cut; nothing shows there).
 */
internal fun contentBox(view: TextView, root: View): RectF {
  val at = rectIn(view, root)
  val layout = view.layout
  val baseline = at.top + view.baseline
  val ink = android.graphics.Rect()
  view.paint.getTextBounds(view.text.toString(), 0, view.text.length, ink)
  return RectF(
    at.left + view.totalPaddingLeft + layout.getLineLeft(0),
    baseline + ink.top,
    at.left + view.totalPaddingLeft + layout.getLineRight(0),
    baseline + ink.bottom,
  )
}

internal fun rectIn(view: View, root: View): RectF {
  var x = 0f
  var y = 0f
  var v: View = view
  while (v !== root) {
    x += v.left - v.scrollX
    y += v.top - v.scrollY
    v = v.parent as View
  }
  return RectF(x, y, x + view.width, y + view.height)
}
