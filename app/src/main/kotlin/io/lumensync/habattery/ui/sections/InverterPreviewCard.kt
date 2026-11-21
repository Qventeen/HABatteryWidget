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
import io.lumensync.habattery.domain.InverterWidgetModel
import io.lumensync.habattery.domain.WidgetModel.EmptyKind
import io.lumensync.habattery.domain.model.InverterFontPercents
import io.lumensync.habattery.domain.model.ThemeChoice
import io.lumensync.habattery.ui.StripRequest
import io.lumensync.habattery.widget.InverterLayoutSpec
import io.lumensync.habattery.widget.InverterWidgetContent
import io.lumensync.habattery.widget.PreviewWidget
import io.lumensync.habattery.widget.WidgetColors

/** Test tag of the drawn strip in the Inverter tab's preview card. */
const val STRIP_PREVIEW_TAG = "strip-preview"

/**
 * The real strip for one size tab, as [WidgetPreviewCard] shows the battery widget: the tabs, the
 * strip drawn by Glance at its design card size, and under it the full sync error or what to set
 * up. It opens on the [request]'s size: 3×1, the size "Add to home screen" places, unless a tapped strip names another.
 */
@Composable
fun InverterPreviewCard(
  model: InverterWidgetModel,
  fonts: InverterFontPercents,
  theme: ThemeChoice,
  request: StripRequest,
  modifier: Modifier = Modifier,
) {
  var spec by rememberSaveable { mutableStateOf(request.spec) }
  var seenCount by rememberSaveable { mutableIntStateOf(request.count) }
  LaunchedEffect(request.count) {
    spec = request.specFor(spec, seenCount)
    seenCount = request.count
  }
  Column(
    modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(12.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
      InverterLayoutSpec.entries.forEachIndexed { index, option ->
        SegmentedButton(
          selected = option == spec,
          onClick = { spec = option },
          shape = SegmentedButtonDefaults.itemShape(index, InverterLayoutSpec.entries.size),
        ) {
          Text(stringResource(R.string.preview_size, option.columns, STRIP_ROWS))
        }
      }
    }
    // The drawing runs later on another thread; it must draw the strip of this composition.
    val shown = spec
    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
      GlancePreview(
        shown.frame,
        listOf(model, fonts, theme),
        maxWidth.value,
        Modifier.testTag(STRIP_PREVIEW_TAG),
      ) {
        PreviewWidget {
          InverterWidgetContent(
            model = model,
            spec = shown,
            colors = WidgetColors(theme),
            fonts = fonts,
            hostScale = 1f,
            openApp = null,
          )
        }
      }
    }
    val setup = model is InverterWidgetModel.Empty && model.kind == EmptyKind.SETUP
    PreviewNote(model.error, if (setup) R.string.inverter_preview_setup else null)
  }
}

private const val STRIP_ROWS = 1

private val InverterLayoutSpec.columns: Int
  get() =
    when (this) {
      InverterLayoutSpec.S3X1 -> 3
      InverterLayoutSpec.S4X1 -> 4
      InverterLayoutSpec.S5X1 -> 5
    }

private val InverterLayoutSpec.frame: PreviewFrame
  get() = PreviewFrame(columns, STRIP_ROWS, bucket, designSize)
