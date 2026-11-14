package io.lumensync.habattery.ui.sections

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import io.lumensync.habattery.R
import io.lumensync.habattery.domain.Field
import io.lumensync.habattery.domain.FieldError
import io.lumensync.habattery.domain.model.ConnectionSettings

const val CONNECTION_SECTION_TAG = "connection_section"

private val UrlKeyboard = IdKeyboard.copy(keyboardType = KeyboardType.Uri)

/** Primary and fallback URL. The drafts reset when the stored values change. */
@Composable
fun ConnectionSection(
  connection: ConnectionSettings,
  errors: List<FieldError>,
  onSave: (primary: String, fallback: String) -> Unit,
  modifier: Modifier = Modifier,
) {
  var primary by rememberSaveable(connection) { mutableStateOf(connection.primaryUrl) }
  var fallback by rememberSaveable(connection) { mutableStateOf(connection.fallbackUrl) }

  SectionCard(R.string.connection_title, modifier.testTag(CONNECTION_SECTION_TAG)) {
    SettingsTextField(
      value = primary,
      onValueChange = { primary = it },
      label = stringResource(R.string.connection_primary),
      error = errors.errorFor(Field.PRIMARY_URL),
      keyboardOptions = UrlKeyboard,
    )
    SettingsTextField(
      value = fallback,
      onValueChange = { fallback = it },
      label = stringResource(R.string.connection_fallback),
      error = errors.errorFor(Field.FALLBACK_URL),
      keyboardOptions = UrlKeyboard,
    )
    SaveButton { onSave(primary, fallback) }
  }
}
