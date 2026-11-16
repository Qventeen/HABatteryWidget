package io.lumensync.habattery.ui.sections

import android.content.Context
import android.view.MotionEvent
import android.widget.FrameLayout
import android.widget.RemoteViews
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.lumensync.habattery.R
import io.lumensync.habattery.domain.WidgetModel
import io.lumensync.habattery.domain.model.FontPercents
import io.lumensync.habattery.domain.model.SyncError
import io.lumensync.habattery.domain.model.ThemeChoice
import io.lumensync.habattery.format.Formatters
import io.lumensync.habattery.ui.PreviewRequest
import io.lumensync.habattery.ui.PreviewTab
import io.lumensync.habattery.widget.LayoutSpec
import io.lumensync.habattery.widget.PreviewWidget
import io.lumensync.habattery.widget.WidgetColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Test tag of the drawn widget in the preview card. */
const val WIDGET_PREVIEW_TAG = "widget-preview"

/**
 * The real widget for one size tab (spec 0006, 5.2): the tabs, then the layout drawn by Glance at
 * its design card size, centred, scaled down only when the screen is narrower. It redraws when the
 * model, the fonts, the theme or the tab change, and takes no taps. Under it, what the widget
 * cannot say: the full sync error (the widget shows only an icon) and what to set up.
 */
@Composable
fun WidgetPreviewCard(
  model: WidgetModel,
  fontPercents: FontPercents,
  theme: ThemeChoice,
  request: PreviewRequest,
  modifier: Modifier = Modifier,
) {
  var tab by rememberSaveable { mutableStateOf(request.tab) }
  // The last request count seen. A new activity starts at count 0, so a recreation (rotation)
  // never replays a request and keeps the user's own tab; only a later, higher count switches it.
  var seenCount by rememberSaveable { mutableIntStateOf(request.count) }
  LaunchedEffect(request.count) {
    if (request.count != seenCount) {
      if (request.count > 0) tab = request.tab
      seenCount = request.count
    }
  }
  Column(
    modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(12.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
      PreviewTab.entries.forEachIndexed { index, option ->
        SegmentedButton(
          selected = option == tab,
          onClick = { tab = option },
          shape = SegmentedButtonDefaults.itemShape(index, PreviewTab.entries.size),
        ) {
          Text(stringResource(R.string.preview_size, option.columns, option.rows))
        }
      }
    }
    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
      WidgetPreview(model, fontPercents, theme, tab.layout, maxWidth.value)
    }
    PreviewNote(model)
  }
}

/**
 * The widget drawn by Glance ([PreviewWidget]) and applied into a [PreviewHost]. The box takes the
 * size of the layout shown, so it changes together with the views, not before them.
 */
@Composable
private fun WidgetPreview(
  model: WidgetModel,
  fontPercents: FontPercents,
  theme: ThemeChoice,
  layout: LayoutSpec,
  availableWidth: Float,
) {
  val context = LocalContext.current
  var drawn by remember { mutableStateOf<Drawn?>(null) }
  LaunchedEffect(context, model, fontPercents, theme, layout) {
    val views =
      withContext(Dispatchers.Default) {
        PreviewWidget(model, fontPercents, WidgetColors(theme)).views(context, layout)
      }
    drawn = Drawn(layout, views)
  }
  val shown = drawn?.layout ?: layout
  val design = shown.designSize
  val scale = PreviewTab.scale(design.width.value, availableWidth)
  val density = LocalDensity.current
  AndroidView(
    factory = ::PreviewHost,
    modifier = Modifier.size(design * scale).testTag(WIDGET_PREVIEW_TAG),
    update = { host ->
      drawn?.let {
        with(density) {
          host.show(it.views, design.width.roundToPx(), design.height.roundToPx())
        }
      }
    },
  )
}

/** The views of one layout, as composed for it. */
private class Drawn(val layout: LayoutSpec, val views: RemoteViews)

/** What the widget cannot say: the full sync error of a reading, or what to set up. */
@Composable
private fun PreviewNote(model: WidgetModel) {
  val context = LocalContext.current
  val error: SyncError? = (model as? WidgetModel.Data)?.error
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
    model is WidgetModel.Empty && model.kind == WidgetModel.EmptyKind.SETUP ->
      Text(
        stringResource(R.string.overview_setup),
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
      )
  }
}

/**
 * Holds the applied widget views at their design size (px) and shows them scaled to its own width,
 * from the top-left corner. It takes every touch, so none reaches the widget's views.
 */
private class PreviewHost(context: Context) : FrameLayout(context) {
  private var shown: RemoteViews? = null
  private var designWidth = 0
  private var designHeight = 0

  fun show(views: RemoteViews, widthPx: Int, heightPx: Int) {
    if (views !== shown) {
      shown = views
      // Fresh views every time: the layouts' roots differ (4×3 is a Column), so another layout's
      // tree must never be reused (no reapply).
      removeAllViews()
      addView(views.apply(context, this))
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

  private fun exactly(px: Int) = MeasureSpec.makeMeasureSpec(px, MeasureSpec.EXACTLY)
}
