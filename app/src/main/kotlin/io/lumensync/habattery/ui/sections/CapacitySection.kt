package io.lumensync.habattery.ui.sections

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import io.lumensync.habattery.R
import io.lumensync.habattery.domain.Field
import io.lumensync.habattery.domain.FieldError
import io.lumensync.habattery.format.formatDecimal
import java.util.Locale

/** Battery capacity in Ah, typed with the user's decimal separator (314,5 in Ukrainian). */
@Composable
fun CapacitySection(
  capacityAh: Double?,
  errors: List<FieldError>,
  onSave: (text: String, locale: Locale) -> Unit,
  modifier: Modifier = Modifier,
) {
  val locale = LocalConfiguration.current.locales[0]
  var text by
    rememberSaveable(capacityAh) {
      mutableStateOf(capacityAh?.let { formatDecimal(it, locale) }.orEmpty())
    }

  SectionCard(R.string.capacity_title, modifier) {
    SettingsTextField(
      value = text,
      onValueChange = { text = it },
      label = stringResource(R.string.capacity_label),
      error = errors.errorFor(Field.CAPACITY),
      hint = stringResource(R.string.capacity_hint),
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
    )
    SaveButton { onSave(text, locale) }
  }
}
