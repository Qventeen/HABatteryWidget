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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import io.lumensync.habattery.R
import io.lumensync.habattery.domain.model.DisplaySettings
import io.lumensync.habattery.domain.model.FontGroup
import io.lumensync.habattery.domain.model.FontPercents
import io.lumensync.habattery.domain.model.MainValue
import io.lumensync.habattery.domain.model.UnderSoc
import kotlin.math.roundToInt

/**
 * What the widget and the overview card show, and their font sizes as percents (app-wide; 50 % is
 * the design). Every change saves at once and redraws the widgets; a slider saves when released.
 */
@Composable
fun WidgetSection(
  display: DisplaySettings,
  fontPercents: FontPercents,
  onSaveDisplay: (DisplaySettings) -> Unit,
  onSaveFontPercents: (FontPercents) -> Unit,
  modifier: Modifier = Modifier,
  initiallyExpanded: Boolean = false,
) {
  val summary =
    stringResource(
      R.string.widget_summary,
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
    OutlinedButton(onClick = { onSaveFontPercents(FontPercents()) }) {
      Text(stringResource(R.string.widget_reset_sizes))
    }
  }
}

/** One font group: its name, the percent, and a slider from 25 to 100 % in steps of 5. */
@Composable
private fun FontSlider(group: FontGroup, stored: Int, onSave: (Int) -> Unit) {
  var value by rememberSaveable(stored) { mutableIntStateOf(stored) }
  val name = stringResource(fontLabel(group))
  Row(Modifier.fillMaxWidth()) {
    Text(name, Modifier.weight(1f))
    Text(stringResource(R.string.widget_font_value, value))
  }
  Slider(
    value = value.toFloat(),
    onValueChange = { value = it.roundToInt() },
    onValueChangeFinished = { onSave(value) },
    valueRange = FontPercents.MIN.toFloat()..FontPercents.MAX.toFloat(),
    steps = (FontPercents.MAX - FontPercents.MIN) / FontPercents.STEP - 1,
    modifier = Modifier.semantics { contentDescription = name },
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
