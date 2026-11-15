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
import io.lumensync.habattery.domain.UrlPolicy
import io.lumensync.habattery.domain.model.ConnectionSettings

const val CONNECTION_SECTION_TAG = "connection_section"

private val UrlKeyboard = IdKeyboard.copy(keyboardType = KeyboardType.Uri)

/** The Home Assistant URL. The draft resets when the stored value changes. */
@Composable
fun ConnectionSection(
  connection: ConnectionSettings,
  errors: List<FieldError>,
  onSave: (url: String) -> Unit,
  modifier: Modifier = Modifier,
  initiallyExpanded: Boolean = false,
) {
  var url by rememberSaveable(connection) { mutableStateOf(connection.url) }
  val warning =
    if (UrlPolicy.check(url) == UrlPolicy.Verdict.PUBLIC_HTTP) {
      stringResource(R.string.connection_http_warning)
    } else {
      null
    }

  val host = runCatching { java.net.URI(connection.url).host }.getOrNull()
  val summary = host ?: stringResource(R.string.summary_not_set)

  SectionCard(
    R.string.connection_title,
    summary,
    initiallyExpanded,
    modifier.testTag(CONNECTION_SECTION_TAG),
  ) {
    SettingsTextField(
      value = url,
      onValueChange = { url = it },
      label = stringResource(R.string.connection_url),
      error = errors.errorFor(Field.URL),
      hint = warning,
      keyboardOptions = UrlKeyboard,
    )
    SaveButton { onSave(url) }
  }
}
