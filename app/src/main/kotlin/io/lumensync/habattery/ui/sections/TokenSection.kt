package io.lumensync.habattery.ui.sections

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import io.lumensync.habattery.R
import io.lumensync.habattery.domain.Field
import io.lumensync.habattery.domain.FieldError

private val TokenKeyboard =
  KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Password)

/**
 * The HA token. The stored token is never shown: the field starts empty, is cleared on save, and
 * says "Token saved" when one is stored. Typing a new value and saving replaces it.
 */
@Composable
fun TokenSection(
  hasToken: Boolean,
  errors: List<FieldError>,
  onSave: (token: String) -> Unit,
  initiallyExpanded: Boolean = false,
  modifier: Modifier = Modifier,
) {
  // Plain remember: a typed token must never enter the saved-state Bundle (lost on rotation).
  var draft by remember { mutableStateOf("") }
  var visible by rememberSaveable { mutableStateOf(false) }

  val summary =
    stringResource(if (hasToken) R.string.token_summary_saved else R.string.summary_not_set)

  SectionCard(R.string.token_title, summary, initiallyExpanded, modifier) {
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
    SaveButton {
      onSave(draft)
      draft = ""
    }
  }
}
