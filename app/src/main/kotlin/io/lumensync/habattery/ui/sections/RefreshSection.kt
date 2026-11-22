package io.lumensync.habattery.ui.sections

import android.content.res.Resources
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import io.lumensync.habattery.R
import io.lumensync.habattery.domain.Field
import io.lumensync.habattery.domain.FieldError
import io.lumensync.habattery.domain.model.RefreshSettings

/** Auto-refresh switch, interval and refresh-on-unlock switch, saved with the Save button. */
@Composable
fun RefreshSection(
  refresh: RefreshSettings,
  errors: List<FieldError>,
  onSave: (auto: Boolean, intervalText: String, onUnlock: Boolean) -> Unit,
  modifier: Modifier = Modifier,
  initiallyExpanded: Boolean = false,
) {
  var auto by rememberSaveable(refresh) { mutableStateOf(refresh.auto) }
  var onUnlock by rememberSaveable(refresh) { mutableStateOf(refresh.onUnlock) }
  var interval by
    rememberSaveable(refresh) { mutableStateOf(refresh.intervalMinutes?.toString().orEmpty()) }

  val summary = refreshSummary(LocalResources.current, refresh)
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
    SwitchRow(
      stringResource(R.string.refresh_on_unlock),
      checked = onUnlock,
      onCheckedChange = { onUnlock = it },
    )
    Text(
      stringResource(R.string.refresh_on_unlock_hint),
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      style = MaterialTheme.typography.bodySmall,
    )
    SaveButton { onSave(auto, interval, onUnlock) }
  }
}

/**
 * The folded section's summary. The timed sync wins; with it off, the unlock refresh still syncs,
 * so only both off read as "Off".
 */
internal fun refreshSummary(resources: Resources, refresh: RefreshSettings): String {
  val minutes = refresh.intervalMinutes
  return when {
    refresh.auto && minutes != null ->
      resources.getQuantityString(R.plurals.refresh_summary_every, minutes, minutes)
    refresh.onUnlock -> resources.getString(R.string.refresh_summary_unlock)
    else -> resources.getString(R.string.refresh_summary_off)
  }
}
