package io.lumensync.habattery.ui.sections

import androidx.annotation.StringRes
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import io.lumensync.habattery.R
import io.lumensync.habattery.domain.Field
import io.lumensync.habattery.domain.EntityFill
import io.lumensync.habattery.domain.FieldError
import io.lumensync.habattery.domain.Validation
import io.lumensync.habattery.domain.model.EntitySettings

/**
 * Saves the prefix, the nine single ids and the temperature ids. Strings only, so the draft
 * survives rotation and process death.
 */
private fun draftSaver(stored: EntitySettings) =
  listSaver<EntitySettings, String>(
    save = {
      listOf(
        it.prefix,
        it.charge,
        it.totalVoltage,
        it.current,
        it.power,
        it.capacityRemaining,
        it.averageCellVoltage,
        it.deltaCellVoltage,
        it.mosfetTemperature,
        it.balancing,
      ) + it.temperatures
    },
    restore = {
      stored.copy(
        prefix = it[0],
        charge = it[1],
        totalVoltage = it[2],
        current = it[3],
        power = it[4],
        capacityRemaining = it[5],
        averageCellVoltage = it[6],
        deltaCellVoltage = it[7],
        mosfetTemperature = it[8],
        balancing = it[9],
        temperatures = it.drop(SINGLE_FIELDS),
      )
    },
  )

private const val SINGLE_FIELDS = 10 // the prefix and the nine single ids

/** Entity IDs: a prefix with Fill, the single ids, and the temperature sensor list. */
@Composable
fun EntitiesSection(
  entities: EntitySettings,
  errors: List<FieldError>,
  onSave: (EntitySettings) -> Unit,
  modifier: Modifier = Modifier,
) {
  var draft by rememberSaveable(entities, stateSaver = draftSaver(entities)) { mutableStateOf(entities) }

  SectionCard(R.string.entities_title, modifier) {
    SettingsTextField(
      value = draft.prefix,
      onValueChange = { draft = draft.copy(prefix = it) },
      label = stringResource(R.string.entities_prefix),
      error = errors.errorFor(Field.PREFIX),
      hint = stringResource(R.string.entities_fill_hint),
    )
    OutlinedButton(
      onClick = { draft = EntityFill.fromPrefix(draft.prefix, draft) },
      enabled = Validation.isPrefixValid(draft.prefix.trim().trimEnd('_')),
    ) {
      Text(stringResource(R.string.entities_fill))
    }

    EntityField(R.string.entity_charge, draft.charge, errors.errorFor(Field.CHARGE)) {
      draft = draft.copy(charge = it)
    }
    EntityField(R.string.entity_total_voltage, draft.totalVoltage, errors.errorFor(Field.TOTAL_VOLTAGE)) {
      draft = draft.copy(totalVoltage = it)
    }
    EntityField(R.string.entity_current, draft.current, errors.errorFor(Field.CURRENT)) {
      draft = draft.copy(current = it)
    }
    EntityField(R.string.entity_power, draft.power, errors.errorFor(Field.POWER)) {
      draft = draft.copy(power = it)
    }
    EntityField(
      R.string.entity_capacity_remaining,
      draft.capacityRemaining,
      errors.errorFor(Field.CAPACITY_REMAINING),
    ) {
      draft = draft.copy(capacityRemaining = it)
    }
    EntityField(
      R.string.entity_average_cell_voltage,
      draft.averageCellVoltage,
      errors.errorFor(Field.AVERAGE_CELL_VOLTAGE),
    ) {
      draft = draft.copy(averageCellVoltage = it)
    }
    EntityField(
      R.string.entity_delta_cell_voltage,
      draft.deltaCellVoltage,
      errors.errorFor(Field.DELTA_CELL_VOLTAGE),
    ) {
      draft = draft.copy(deltaCellVoltage = it)
    }
    EntityField(
      R.string.entity_mosfet_temperature,
      draft.mosfetTemperature,
      errors.errorFor(Field.MOSFET_TEMPERATURE),
    ) {
      draft = draft.copy(mosfetTemperature = it)
    }
    EntityField(R.string.entity_balancing, draft.balancing, errors.errorFor(Field.BALANCING)) {
      draft = draft.copy(balancing = it)
    }

    TemperatureList(
      temperatures = draft.temperatures,
      errors = errors,
      onChange = { draft = draft.copy(temperatures = it) },
    )

    SaveButton { onSave(draft) }
  }
}

@Composable
private fun TemperatureList(
  temperatures: List<String>,
  errors: List<FieldError>,
  onChange: (List<String>) -> Unit,
) {
  Text(stringResource(R.string.entity_temperatures), style = MaterialTheme.typography.titleSmall)
  temperatures.forEachIndexed { index, id ->
    val number = index + 1
    SettingsTextField(
      value = id,
      onValueChange = { value -> onChange(temperatures.toMutableList().also { it[index] = value }) },
      label = stringResource(R.string.entity_temperature, number),
      error = errors.errorFor(Field.TEMPERATURES, index),
      trailingIcon = {
        IconButton(onClick = { onChange(temperatures.filterIndexed { i, _ -> i != index }) }) {
          Icon(
            painterResource(R.drawable.ic_close),
            contentDescription = stringResource(R.string.entity_temperature_remove, number),
          )
        }
      },
    )
  }
  errors.errorFor(Field.TEMPERATURES)?.let {
    Text(fieldErrorText(it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
  }
  OutlinedButton(
    onClick = { onChange(temperatures + "") },
    enabled = temperatures.size < EntitySettings.MAX_TEMPERATURES,
  ) {
    Text(stringResource(R.string.entity_temperature_add))
  }
}

@Composable
private fun EntityField(
  @StringRes label: Int,
  value: String,
  error: FieldError?,
  onValueChange: (String) -> Unit,
) {
  SettingsTextField(value, onValueChange, stringResource(label), error)
}
