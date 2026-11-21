package io.lumensync.habattery.ui.sections

import android.content.Context
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.RemoteViews
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.viewinterop.AndroidView
import io.lumensync.habattery.R
import io.lumensync.habattery.domain.model.SyncError
import io.lumensync.habattery.format.Formatters
import io.lumensync.habattery.ui.PreviewTab
import io.lumensync.habattery.widget.PreviewWidget
import io.lumensync.habattery.widget.previewOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * One size tab of a widget preview: its home-screen cells, the bucket Glance composes it at, and
 * the design card it is shown at.
 */
internal data class PreviewFrame(
  val columns: Int,
  val rows: Int,
  val bucket: DpSize,
  val designSize: DpSize,
)

/**
 * The widget that [widget] builds, drawn by Glance at the bucket of [frame] and applied into a
 * [PreviewHost] at its design size, scaled down only when [availableWidth] is narrower. It draws
 * again when [frame] or [inputs] change, so [inputs] must hold every value [widget] reads. The box
 * takes the size of the frame shown, so it changes together with the views, not before them; so
 * does its TalkBack name.
 */
@Composable
internal fun GlancePreview(
  frame: PreviewFrame,
  inputs: List<Any?>,
  availableWidth: Float,
  modifier: Modifier = Modifier,
  widget: () -> PreviewWidget,
) {
  val context = LocalContext.current
  var drawn by remember { mutableStateOf<Drawn?>(null) }
  LaunchedEffect(context, frame, inputs) {
    val views = withContext(Dispatchers.Default) { widget().views(context, frame.bucket) }
    drawn = Drawn(frame, views)
  }
  val shown = drawn?.frame ?: frame
  val design = shown.designSize
  val scale = PreviewTab.scale(design.width.value, availableWidth)
  val density = LocalDensity.current
  val description =
    stringResource(R.string.preview_description, stringResource(R.string.preview_size, shown.columns, shown.rows))
  AndroidView(
    factory = ::PreviewHost,
    modifier = modifier.size(design * scale),
    update = { host ->
      host.contentDescription = description
      drawn?.let {
        with(density) {
          host.show(it, design.width.roundToPx(), design.height.roundToPx())
        }
      }
    },
  )
}

/**
 * What the widget cannot say: the full sync [error] (with data the widget shows only an icon,
 * without it a line cut to fit), else [setupText] when the widget needs setup.
 */
@Composable
internal fun PreviewNote(error: SyncError?, @StringRes setupText: Int?) {
  val context = LocalContext.current
  when {
    error != null -> {
      val reason = remember(context, error) { Formatters(context).error(error) }
      Text(
        stringResource(R.string.widget_sync_error, reason),
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
      )
    }
    setupText != null ->
      Text(
        stringResource(setupText),
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
      )
  }
}

/** The views of one frame, as composed for it; null when Glance failed to draw them. */
private class Drawn(val frame: PreviewFrame, val views: RemoteViews?)

/**
 * Holds the applied widget views at their design size (px) and shows them scaled to its own width,
 * from the top-left corner. Views that fail to draw or to apply show as the home screen shows a
 * broken widget. It takes every touch, so none reaches the widget's views.
 */
private class PreviewHost(context: Context) : FrameLayout(context) {
  private var shown: Drawn? = null
  private var designWidth = 0
  private var designHeight = 0

  fun show(drawn: Drawn, widthPx: Int, heightPx: Int) {
    if (drawn !== shown) {
      shown = drawn
      // Fresh views every time: the layouts' roots differ (4×3 is a Column), so another layout's
      // tree must never be reused (no reapply).
      removeAllViews()
      val applied = drawn.views?.let { views -> previewOrNull("apply") { views.apply(context, this) } }
      addView(applied ?: errorView())
    }
    if (widthPx != designWidth || heightPx != designHeight) {
      designWidth = widthPx
      designHeight = heightPx
      requestLayout()
    }
  }

  override fun onInterceptTouchEvent(ev: MotionEvent): Boolean = true

  override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
    getChildAt(0)?.measure(exactly(designWidth), exactly(designHeight))
    setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.getSize(heightMeasureSpec))
  }

  override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
    val child = getChildAt(0) ?: return
    child.layout(0, 0, designWidth, designHeight)
    val scale = if (designWidth > 0) width.toFloat() / designWidth else 1f
    child.pivotX = 0f
    child.pivotY = 0f
    child.scaleX = scale
    child.scaleY = scale
  }

  private fun errorView(): View = LayoutInflater.from(context).inflate(R.layout.widget_error, this, false)

  private fun exactly(px: Int) = MeasureSpec.makeMeasureSpec(px, MeasureSpec.EXACTLY)
}
