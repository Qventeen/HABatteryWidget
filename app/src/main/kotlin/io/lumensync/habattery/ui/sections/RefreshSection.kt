package io.lumensync.habattery.ui.sections

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import io.lumensync.habattery.R
import io.lumensync.habattery.domain.Field
import io.lumensync.habattery.domain.FieldError
import io.lumensync.habattery.domain.model.RefreshSettings

/** Auto-refresh switch and interval. Both are saved together with the Save button. */
@Composable
fun RefreshSection(
  refresh: RefreshSettings,
  errors: List<FieldError>,
  onSave: (auto: Boolean, intervalText: String) -> Unit,
  modifier: Modifier = Modifier,
  initiallyExpanded: Boolean = false,
) {
  var auto by rememberSaveable(refresh) { mutableStateOf(refresh.auto) }
  var interval by
    rememberSaveable(refresh) { mutableStateOf(refresh.intervalMinutes?.toString().orEmpty()) }

  val minutes = refresh.intervalMinutes
  val summary =
    if (refresh.auto && minutes != null) {
      pluralStringResource(R.plurals.refresh_summary_every, minutes, minutes)
    } else {
      stringResource(R.string.refresh_summary_off)
    }

  SectionCard(R.string.refresh_title, summary, initiallyExpanded, modifier) {
    SwitchRow(stringResource(R.string.refresh_auto), checked = auto, onCheckedChange = { auto = it })
    SettingsTextField(
      value = interval,
      onValueChange = { interval = it },
      label = stringResource(R.string.refresh_interval),
      error = errors.errorFor(Field.INTERVAL),
      hint =
        stringResource(
          R.string.refresh_interval_range,
          RefreshSettings.MIN_INTERVAL,
          RefreshSettings.MAX_INTERVAL,
        ),
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    )
    SaveButton { onSave(auto, interval) }
  }
}
