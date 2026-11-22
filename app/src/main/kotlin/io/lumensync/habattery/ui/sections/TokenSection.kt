package io.lumensync.habattery.ui.sections

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import io.lumensync.habattery.R
import io.lumensync.habattery.domain.Field
import io.lumensync.habattery.domain.FieldError
import io.lumensync.habattery.domain.model.TokenType

private val TokenKeyboard =
  KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Password)

/**
 * The token type and the HA token of that type; [hasToken] and the summary are about the selected
 * type's slot. A tap on the other type saves it at once (no Save) and syncs; a tap on the selected
 * type does nothing. The stored token is never shown: the field starts empty, is cleared on save,
 * and says "Token saved" when one is stored. Typing a new value and saving replaces it; Remove,
 * after a confirm, clears the selected slot only.
 */
@Composable
fun TokenSection(
  tokenType: TokenType,
  hasToken: Boolean,
  showTypeHint: Boolean,
  errors: List<FieldError>,
  onSave: (token: String) -> Unit,
  onTypeChange: (TokenType) -> Unit,
  onRemove: () -> Unit,
  modifier: Modifier = Modifier,
  initiallyExpanded: Boolean = false,
) {
  // Plain remember: a typed token must never enter the saved-state Bundle (lost on rotation).
  // Keyed by the type: a draft typed for one slot must never be saved into the other.
  var draft by remember(tokenType) { mutableStateOf("") }
  var visible by rememberSaveable { mutableStateOf(false) }
  var confirmRemove by rememberSaveable { mutableStateOf(false) }

  val summary =
    stringResource(
      R.string.token_summary_with_type,
      stringResource(if (hasToken) R.string.token_summary_saved else R.string.summary_not_set),
      stringResource(tokenTypeLabel(tokenType)),
    )

  SectionCard(R.string.token_title, summary, initiallyExpanded, modifier) {
    ChoiceRow(R.string.token_type_label, TokenType.entries, tokenType, ::tokenTypeLabel) {
      if (it != tokenType) {
        // Cleared at the tap too: the key changes only when the new type is stored, and a Save in
        // between would write the draft into the new slot.
        draft = ""
        onTypeChange(it)
      }
    }
    SettingsTextField(
      value = draft,
      onValueChange = { draft = it },
      label = stringResource(R.string.token_label),
      error = errors.errorFor(Field.TOKEN),
      hint = if (hasToken) stringResource(R.string.token_saved) else null,
      keyboardOptions = TokenKeyboard,
      visualTransformation =
        if (visible) VisualTransformation.None else PasswordVisualTransformation(),
      trailingIcon = {
        IconButton(onClick = { visible = !visible }) {
          Icon(
            painter =
              painterResource(if (visible) R.drawable.ic_visibility_off else R.drawable.ic_visibility),
            contentDescription =
              stringResource(if (visible) R.string.token_hide else R.string.token_show),
          )
        }
      },
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      SaveButton {
        onSave(draft)
        draft = ""
      }
      if (hasToken) {
        OutlinedButton(onClick = { confirmRemove = true }) {
          Text(stringResource(R.string.token_remove))
        }
      }
    }
    if (showTypeHint) {
      Text(
        stringResource(R.string.token_type_hint),
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodySmall,
      )
    }
  }

  if (confirmRemove) {
    RemoveTokenDialog(
      tokenType,
      onConfirm = {
        confirmRemove = false
        onRemove()
      },
      onDismiss = { confirmRemove = false },
    )
  }
}

@Composable
private fun RemoveTokenDialog(tokenType: TokenType, onConfirm: () -> Unit, onDismiss: () -> Unit) {
  AlertDialog(
    onDismissRequest = onDismiss,
    confirmButton = {
      TextButton(onClick = onConfirm) { Text(stringResource(R.string.token_remove_confirm)) }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_cancel)) }
    },
    title = { Text(stringResource(removeTitle(tokenType))) },
  )
}

@StringRes
private fun tokenTypeLabel(type: TokenType): Int =
  when (type) {
    TokenType.ADMIN -> R.string.token_type_admin
    TokenType.USER -> R.string.token_type_user
  }

@StringRes
private fun removeTitle(type: TokenType): Int =
  when (type) {
    TokenType.ADMIN -> R.string.token_remove_title_admin
    TokenType.USER -> R.string.token_remove_title_user
  }
