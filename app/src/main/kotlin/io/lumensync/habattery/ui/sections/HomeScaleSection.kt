package io.lumensync.habattery.ui.sections

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import io.lumensync.habattery.R
import io.lumensync.habattery.domain.model.HomeScale
import kotlin.math.roundToInt

/**
 * The home-screen scale (80–200 %, 100 = as reported), one for both widgets: the same launcher
 * draws them (spec 0012). The slider saves when released and redraws the widgets; one button puts
 * the scale back to 100 %.
 */
@Composable
fun HomeScaleSection(
  homeScalePercent: Int,
  onSaveHomeScale: (Int) -> Unit,
  onReset: () -> Unit,
  modifier: Modifier = Modifier,
  initiallyExpanded: Boolean = false,
) {
  val summary = stringResource(R.string.widget_font_value, homeScalePercent)
  SectionCard(R.string.widget_scale_label, summary, initiallyExpanded, modifier) {
    HomeScaleSlider(homeScalePercent, onSaveHomeScale)
    OutlinedButton(onClick = onReset) { Text(stringResource(R.string.widget_reset_scale)) }
  }
}

/** The home-screen scale: a slider from 80 to 200 % in steps of 1, and a line saying what it is for. */
@Composable
private fun HomeScaleSlider(stored: Int, onSave: (Int) -> Unit) {
  var dragValue by rememberSaveable { mutableStateOf<Int?>(null) }
  val value = dragValue ?: stored
  val name = stringResource(R.string.widget_scale_label)
  val shown = stringResource(R.string.widget_font_value, value)
  Row(Modifier.fillMaxWidth()) {
    Text(name, Modifier.weight(1f))
    Text(shown)
  }
  Slider(
    value = value.toFloat(),
    onValueChange = { dragValue = it.roundToInt() },
    onValueChangeFinished = {
      onSave(dragValue ?: stored)
      dragValue = null
    },
    valueRange = HomeScale.MIN.toFloat()..HomeScale.MAX.toFloat(),
    steps = HomeScale.MAX - HomeScale.MIN - 1,
    modifier =
      Modifier.semantics {
        contentDescription = name
        stateDescription = shown
      },
  )
  Text(
    stringResource(R.string.widget_scale_hint),
    style = MaterialTheme.typography.bodySmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
  )
}
