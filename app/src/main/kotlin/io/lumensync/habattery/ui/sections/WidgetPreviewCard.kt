package io.lumensync.habattery.ui.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.lumensync.habattery.R
import io.lumensync.habattery.domain.WidgetModel
import io.lumensync.habattery.domain.model.FontPercents
import io.lumensync.habattery.domain.model.SyncError
import io.lumensync.habattery.domain.model.ThemeChoice
import io.lumensync.habattery.ui.PreviewRequest
import io.lumensync.habattery.ui.PreviewTab
import io.lumensync.habattery.widget.PreviewWidget
import io.lumensync.habattery.widget.WidgetColors
import io.lumensync.habattery.widget.WidgetContent

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
  var seenCount by rememberSaveable { mutableIntStateOf(request.count) }
  LaunchedEffect(request.count) {
    tab = request.tabFor(tab, seenCount)
    seenCount = request.count
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
      GlancePreview(
        tab.frame,
        listOf(model, fontPercents, theme),
        maxWidth.value,
        Modifier.testTag(WIDGET_PREVIEW_TAG),
      ) {
        PreviewWidget { WidgetContent(model, fontPercents, WidgetColors(theme), actions = null) }
      }
    }
    val setup = model is WidgetModel.Empty && model.kind == WidgetModel.EmptyKind.SETUP
    PreviewNote(model.error, if (setup) R.string.overview_setup else null)
  }
}

private val PreviewTab.frame: PreviewFrame
  get() = PreviewFrame(columns, rows, layout.bucket, layout.designSize)

private val WidgetModel.error: SyncError?
  get() =
    when (this) {
      is WidgetModel.Data -> error
      is WidgetModel.Empty -> error
    }
