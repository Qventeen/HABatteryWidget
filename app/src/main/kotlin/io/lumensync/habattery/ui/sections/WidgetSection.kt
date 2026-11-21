package io.lumensync.habattery.ui.sections

import androidx.annotation.StringRes
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.lumensync.habattery.R
import io.lumensync.habattery.domain.model.DisplaySettings
import io.lumensync.habattery.domain.model.FontGroup
import io.lumensync.habattery.domain.model.FontPercents
import io.lumensync.habattery.domain.model.MainValue
import io.lumensync.habattery.domain.model.UnderSoc

/**
 * What the battery widget shows and its font sizes as percents (50 % is the design size, 1.5 × it
 * for the small texts). Every change saves at once and redraws the widgets; a slider saves when
 * released. One button puts the font sizes back to the defaults; the home-screen scale has its own
 * section.
 */
@Composable
fun WidgetSection(
  display: DisplaySettings,
  fontPercents: FontPercents,
  textSizeChanged: Boolean,
  onSaveDisplay: (DisplaySettings) -> Unit,
  onSaveFontPercents: (FontPercents) -> Unit,
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
      FontSlider(fontLabel(group), fontPercents[group]) {
        onSaveFontPercents(fontPercents.with(group, it))
      }
    }
    OutlinedButton(onClick = onReset) { Text(stringResource(R.string.widget_reset_sizes)) }
  }
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
