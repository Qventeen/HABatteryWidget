package io.lumensync.habattery.ui.sections

import android.appwidget.AppWidgetManager
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
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
 * "Add to home screen" for one widget type, only when the launcher can pin. The launcher places a
 * pinned widget at its default size and an app cannot ask for another, so [hint] names that size.
 */
@Composable
fun AddWidgetRow(@StringRes hint: Int, onAddWidget: () -> Unit, modifier: Modifier = Modifier) {
  val context = LocalContext.current
  val canPin =
    remember(context) { AppWidgetManager.getInstance(context).isRequestPinAppWidgetSupported }

  if (canPin) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
      OutlinedButton(onClick = onAddWidget, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.action_add_widget))
      }
      Text(
        stringResource(hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}
