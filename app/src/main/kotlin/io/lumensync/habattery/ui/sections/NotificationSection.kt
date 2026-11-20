package io.lumensync.habattery.ui.sections

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.lumensync.habattery.R

/**
 * The main notification switch; the bells in Colours choose the levels. On Android 13+ turning it on
 * asks for the notification permission first; when it is denied the switch stays off and a link to
 * the settings appears. Levels are checked only when a sync runs, so without [autoRefresh] a note
 * says that nothing can come.
 */
@Composable
fun NotificationSection(
  enabled: Boolean,
  autoRefresh: Boolean,
  onEnabledChange: (Boolean) -> Unit,
  onOpenSettings: () -> Unit,
  modifier: Modifier = Modifier,
  initiallyExpanded: Boolean = false,
) {
  var denied by rememberSaveable { mutableStateOf(false) }
  val permission =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
      denied = !granted
      if (granted) onEnabledChange(true)
    }

  val summary =
    stringResource(if (enabled) R.string.notify_summary_on else R.string.notify_summary_off)

  SectionCard(R.string.notify_title, summary, initiallyExpanded, modifier) {
    SwitchRow(
      stringResource(R.string.notifications_switch),
      checked = enabled,
      onCheckedChange = { on ->
        when {
          !on -> onEnabledChange(false)
          Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
          else -> onEnabledChange(true)
        }
      },
    )
    Text(
      stringResource(R.string.notifications_hint),
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      style = MaterialTheme.typography.bodySmall,
    )
    if (!autoRefresh) {
      Text(
        stringResource(R.string.notifications_no_auto_refresh),
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodySmall,
      )
    }
    if (denied) {
      Text(
        stringResource(R.string.notify_blocked),
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodySmall,
      )
      TextButton(onClick = onOpenSettings) { Text(stringResource(R.string.notify_open_settings)) }
    }
  }
}
