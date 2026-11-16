package io.lumensync.habattery.ui.sections

import android.appwidget.AppWidgetManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.lumensync.habattery.R

/**
 * "Refresh now" (busy while a sync runs) and "Add widget" (only when the launcher can pin). The
 * launcher places a pinned widget at its default size (4×2); an app cannot ask for another, so a
 * line under the buttons says so.
 */
@Composable
fun ActionsRow(
  syncing: Boolean,
  onRefresh: () -> Unit,
  onAddWidget: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val canPin =
    remember(context) { AppWidgetManager.getInstance(context).isRequestPinAppWidgetSupported }

  Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
  Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    Button(onClick = onRefresh, enabled = !syncing, modifier = Modifier.weight(1f)) {
      if (syncing) {
        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
        Spacer(Modifier.width(8.dp))
      }
      Text(stringResource(R.string.action_refresh_now))
    }
    if (canPin) {
      OutlinedButton(onClick = onAddWidget, modifier = Modifier.weight(1f)) {
        Text(stringResource(R.string.action_add_widget))
      }
    }
  }
  if (canPin) {
    Text(
      stringResource(R.string.action_add_widget_hint),
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
  }
}
