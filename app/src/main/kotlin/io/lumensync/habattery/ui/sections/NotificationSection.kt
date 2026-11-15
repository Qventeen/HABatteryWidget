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
 * The full-charge notification switch. On Android 13+ turning it on asks for the notification
 * permission first; when it is denied the switch stays off and a link to the settings appears.
 */
@Composable
fun NotificationSection(
  enabled: Boolean,
  onSetEnabled: (Boolean) -> Unit,
  onOpenSettings: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var denied by rememberSaveable { mutableStateOf(false) }
  val permission =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
      denied = !granted
      if (granted) onSetEnabled(true)
    }

  SectionCard(R.string.notify_title, modifier) {
    SwitchRow(
      stringResource(R.string.notify_on_full),
      checked = enabled,
      onCheckedChange = { on ->
        when {
          !on -> onSetEnabled(false)
          Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
          else -> onSetEnabled(true)
        }
      },
    )
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
