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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import io.lumensync.habattery.R
import io.lumensync.habattery.domain.model.DisplaySettings
import io.lumensync.habattery.domain.model.FontGroup
import io.lumensync.habattery.domain.model.FontSizes
import io.lumensync.habattery.domain.model.MainValue
import io.lumensync.habattery.domain.model.UnderSoc
import io.lumensync.habattery.format.formatDecimal
import kotlin.math.roundToInt

/**
 * What the widget and the overview card show, and their font sizes (app-wide). Every change saves
 * at once and redraws the widgets; a slider saves when it is released.
 */
@Composable
fun WidgetSection(
  display: DisplaySettings,
  fontSizes: FontSizes,
  onSaveDisplay: (DisplaySettings) -> Unit,
  onSaveFontSizes: (FontSizes) -> Unit,
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
      FontSlider(group, fontSizes[group]) { onSaveFontSizes(fontSizes.with(group, it)) }
    }
    OutlinedButton(onClick = { onSaveFontSizes(FontSizes()) }) {
      Text(stringResource(R.string.widget_reset_sizes))
    }
  }
}

/** One font group: its name, the value in sp, and a slider over the group's range and step. */
@Composable
private fun FontSlider(group: FontGroup, stored: Float, onSave: (Float) -> Unit) {
  val locale = LocalConfiguration.current.locales[0]
  var value by rememberSaveable(stored) { mutableFloatStateOf(stored) }
  val name = stringResource(fontLabel(group))
  Row(Modifier.fillMaxWidth()) {
    Text(name, Modifier.weight(1f))
    Text(stringResource(R.string.widget_font_value, formatDecimal(value.toDouble(), locale)))
  }
  Slider(
    value = value,
    onValueChange = { value = it },
    onValueChangeFinished = { onSave(value) },
    valueRange = group.min..group.max,
    steps = ((group.max - group.min) / group.step).roundToInt() - 1,
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
