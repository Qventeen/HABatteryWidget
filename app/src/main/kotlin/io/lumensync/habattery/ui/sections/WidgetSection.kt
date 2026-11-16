package io.lumensync.habattery.ui.sections

import androidx.annotation.StringRes
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
import io.lumensync.habattery.domain.model.DisplaySettings
import io.lumensync.habattery.domain.model.FontGroup
import io.lumensync.habattery.domain.model.FontPercents
import io.lumensync.habattery.domain.model.HomeScale
import io.lumensync.habattery.domain.model.MainValue
import io.lumensync.habattery.domain.model.UnderSoc
import kotlin.math.roundToInt

/**
 * What the widget shows, its font sizes as percents (app-wide; 50 % is the design) and the
 * home-screen scale (80–200 %, 100 = as reported). Every change saves at once and redraws the
 * widgets; a slider saves when released. One button puts the font sizes and the scale back to the
 * defaults.
 */
@Composable
fun WidgetSection(
  display: DisplaySettings,
  fontPercents: FontPercents,
  homeScalePercent: Int,
  textSizeChanged: Boolean,
  onSaveDisplay: (DisplaySettings) -> Unit,
  onSaveFontPercents: (FontPercents) -> Unit,
  onSaveHomeScale: (Int) -> Unit,
  onReset: () -> Unit,
  modifier: Modifier = Modifier,
  initiallyExpanded: Boolean = false,
) {
  val summary =
    stringResource(
      if (textSizeChanged) R.string.widget_summary_changed else R.string.widget_summary,
      stringResource(underSocLabel(display.underSoc)),
      stringResource(mainValueLabel(display.mainValue)),
    )

  SectionCard(R.string.widget_settings_title, summary, initiallyExpanded, modifier) {
    ChoiceRow(R.string.widget_under_soc, UnderSoc.entries, display.underSoc, ::underSocLabel) {
      onSaveDisplay(display.copy(underSoc = it))
    }
    ChoiceRow(R.string.widget_second_value, MainValue.entries, display.mainValue, ::mainValueLabel) {
      onSaveDisplay(display.copy(mainValue = it))
    }
    FontGroup.entries.forEach { group ->
      FontSlider(group, fontPercents[group]) { onSaveFontPercents(fontPercents.with(group, it)) }
    }
    HomeScaleSlider(homeScalePercent, onSaveHomeScale)
    OutlinedButton(onClick = onReset) { Text(stringResource(R.string.widget_reset_sizes)) }
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

/** One font group: its name, the percent, and a slider from 10 to 100 % in steps of 5. */
@Composable
private fun FontSlider(group: FontGroup, stored: Int, onSave: (Int) -> Unit) {
  var dragValue by rememberSaveable { mutableStateOf<Int?>(null) }
  val value = dragValue ?: stored
  val name = stringResource(fontLabel(group))
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
    valueRange = FontPercents.MIN.toFloat()..FontPercents.MAX.toFloat(),
    steps = (FontPercents.MAX - FontPercents.MIN) / FontPercents.STEP - 1,
    modifier =
      Modifier.semantics {
        contentDescription = name
        stateDescription = shown
      },
  )
}

@StringRes
private fun underSocLabel(choice: UnderSoc): Int =
  when (choice) {
    UnderSoc.KWH -> R.string.widget_choice_kwh
    UnderSoc.AH -> R.string.widget_choice_ah
  }

@StringRes
private fun mainValueLabel(choice: MainValue): Int =
  when (choice) {
    MainValue.CURRENT -> R.string.widget_choice_current
    MainValue.POWER -> R.string.widget_choice_power
  }

@StringRes
private fun fontLabel(group: FontGroup): Int =
  when (group) {
    FontGroup.SOC -> R.string.widget_font_soc
    FontGroup.UNDER_SOC -> R.string.widget_font_under_soc
    FontGroup.MAIN -> R.string.widget_font_main
    FontGroup.CELL -> R.string.widget_font_cell
    FontGroup.SMALL -> R.string.widget_font_small
  }
