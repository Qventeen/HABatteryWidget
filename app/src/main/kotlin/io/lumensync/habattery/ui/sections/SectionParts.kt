package io.lumensync.habattery.ui.sections

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import io.lumensync.habattery.R
import io.lumensync.habattery.domain.Field
import io.lumensync.habattery.domain.FieldError

/** Keyboard for entity IDs and URLs: no auto-correct, no capitals. */
internal val IdKeyboard = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Ascii)

/** A titled card that holds one settings section. */
@Composable
internal fun SectionCard(
  @StringRes title: Int,
  modifier: Modifier = Modifier,
  content: @Composable ColumnScope.() -> Unit,
) {
  OutlinedCard(modifier.fillMaxWidth()) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
      content()
    }
  }
}

@Composable
internal fun SaveButton(onClick: () -> Unit) {
  Button(onClick = onClick) { Text(stringResource(R.string.action_save)) }
}

/** A one-line input. Below it: the error from the last save, else the [hint]. */
@Composable
internal fun SettingsTextField(
  value: String,
  onValueChange: (String) -> Unit,
  label: String,
  error: FieldError?,
  hint: String? = null,
  keyboardOptions: KeyboardOptions = IdKeyboard,
  visualTransformation: VisualTransformation = VisualTransformation.None,
  trailingIcon: @Composable (() -> Unit)? = null,
) {
  val below = if (error != null) fieldErrorText(error) else hint
  OutlinedTextField(
    value = value,
    onValueChange = onValueChange,
    modifier = Modifier.fillMaxWidth(),
    label = { Text(label) },
    supportingText = below?.let { { Text(it) } },
    isError = error != null,
    singleLine = true,
    keyboardOptions = keyboardOptions,
    visualTransformation = visualTransformation,
    trailingIcon = trailingIcon,
  )
}

/** A label with a switch; the whole row toggles. */
@Composable
internal fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
  Row(
    modifier =
      Modifier.fillMaxWidth()
        .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(label, modifier = Modifier.weight(1f).padding(end = 16.dp))
    Switch(checked = checked, onCheckedChange = null)
  }
}

/** The error the last save reported for [field] (and [cellIndex] for one cell), if any. */
internal fun List<FieldError>.errorFor(field: Field, cellIndex: Int? = null): FieldError? =
  firstOrNull { it.field == field && it.cellIndex == cellIndex }
