package io.lumensync.habattery.ui.sections

import androidx.annotation.StringRes
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.lumensync.habattery.R
import io.lumensync.habattery.domain.model.InverterFontPercents
import io.lumensync.habattery.domain.model.ModeStyle

/**
 * How the strip shows the mode, and its two font sizes as percents (50 % is the design size), as
 * the battery's Widget section: every change saves at once and redraws the widgets. "Reset sizes"
 * puts only the fonts back.
 */
@Composable
fun InverterDisplaySection(
  style: ModeStyle,
  fonts: InverterFontPercents,
  onSaveStyle: (ModeStyle) -> Unit,
  onSaveFonts: (InverterFontPercents) -> Unit,
  onReset: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val styleText = stringResource(styleLabel(style))
  val summary =
    if (fonts == InverterFontPercents()) styleText
    else stringResource(R.string.inverter_display_summary_changed, styleText)

  SectionCard(R.string.inverter_display_title, summary, initiallyExpanded = false, modifier) {
    ChoiceRow(R.string.inverter_mode_style, ModeStyle.entries, style, ::styleLabel) {
      onSaveStyle(it)
    }
    FontSlider(R.string.inverter_font_word, fonts.word) { onSaveFonts(fonts.copy(word = it)) }
    FontSlider(R.string.inverter_font_value, fonts.value) { onSaveFonts(fonts.copy(value = it)) }
    OutlinedButton(onClick = onReset) { Text(stringResource(R.string.widget_reset_sizes)) }
  }
}

@StringRes
private fun styleLabel(style: ModeStyle): Int =
  when (style) {
    ModeStyle.BIG_ICON -> R.string.inverter_style_big_icon
    ModeStyle.ICON_WORD -> R.string.inverter_style_icon_word
  }
