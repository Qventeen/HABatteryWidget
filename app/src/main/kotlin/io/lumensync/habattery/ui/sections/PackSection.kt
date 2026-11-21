package io.lumensync.habattery.ui.sections

import androidx.annotation.StringRes
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import io.lumensync.habattery.domain.model.BatteryType
import io.lumensync.habattery.format.formatDecimal
import java.util.Locale

/**
 * The pack the default levels are built for (spec 0013). The type stores at the tap; the cell
 * count and the capacity (typed with the user's decimal separator, 314,5 in Ukrainian) share one
 * Save. A `null` type shows as [BatteryType.DEFAULT].
 */
@Composable
fun PackSection(
  batteryType: BatteryType?,
  cellCount: Int?,
  capacityAh: Double?,
  errors: List<FieldError>,
  onTypeChange: (BatteryType) -> Unit,
  onSave: (cells: String, capacity: String, locale: Locale) -> Unit,
  modifier: Modifier = Modifier,
  initiallyExpanded: Boolean = false,
) {
  val locale = LocalConfiguration.current.locales[0]
  val type = batteryType ?: BatteryType.DEFAULT
  var cellsText by rememberSaveable(cellCount) { mutableStateOf(cellCount?.toString().orEmpty()) }
  var capacityText by
    rememberSaveable(capacityAh) {
      mutableStateOf(capacityAh?.let { formatDecimal(it, locale) }.orEmpty())
    }

  val summary =
    listOfNotNull(
        stringResource(batteryTypeLabel(type)),
        cellCount?.let { stringResource(R.string.pack_summary_cells, it) },
        capacityAh?.let { stringResource(R.string.capacity_summary, formatDecimal(it, locale)) },
      )
      .reduce { shown, part -> stringResource(R.string.pack_summary_join, shown, part) }

  SectionCard(R.string.pack_title, summary, initiallyExpanded, modifier) {
    ChoiceRow(R.string.pack_type_label, BatteryType.entries, type, ::batteryTypeLabel) {
      onTypeChange(it)
    }
    Text(
      stringResource(R.string.pack_type_hint),
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    SettingsTextField(
      value = cellsText,
      onValueChange = { cellsText = it },
      label = stringResource(R.string.cells_label),
      error = errors.errorFor(Field.CELLS),
      hint = stringResource(R.string.cells_hint),
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    )
    SettingsTextField(
      value = capacityText,
      onValueChange = { capacityText = it },
      label = stringResource(R.string.capacity_label),
      error = errors.errorFor(Field.CAPACITY),
      hint = stringResource(R.string.capacity_hint),
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
    )
    SaveButton { onSave(cellsText, capacityText, locale) }
  }
}

@StringRes
private fun batteryTypeLabel(type: BatteryType): Int =
  when (type) {
    BatteryType.LIFEPO4 -> R.string.battery_type_lifepo4
    BatteryType.LI_ION -> R.string.battery_type_li_ion
    BatteryType.LTO -> R.string.battery_type_lto
  }
