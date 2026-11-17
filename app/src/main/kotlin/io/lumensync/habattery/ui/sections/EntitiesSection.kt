package io.lumensync.habattery.ui.sections

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.lumensync.habattery.R
import io.lumensync.habattery.domain.Field
import io.lumensync.habattery.domain.EntityFill
import io.lumensync.habattery.domain.FieldError
import io.lumensync.habattery.domain.Problem
import io.lumensync.habattery.domain.Validation
import io.lumensync.habattery.domain.model.EntitySettings
import io.lumensync.habattery.format.Formatters
import io.lumensync.habattery.ui.SensorFill
import io.lumensync.habattery.ui.SensorSearch
import kotlinx.coroutines.launch

/**
 * Saves the prefix, the ten single ids and the temperature ids. Strings only, so the draft
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
        it.totalCapacity,
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
        totalCapacity = it[6],
        averageCellVoltage = it[7],
        deltaCellVoltage = it[8],
        mosfetTemperature = it[9],
        balancing = it[10],
        temperatures = it.drop(SINGLE_FIELDS),
      )
    },
  )

private const val SINGLE_FIELDS = 11 // the prefix and the ten single ids

/** Entity IDs: a prefix with Fill, the single ids, and the temperature sensor list. */
@Composable
fun EntitiesSection(
  entities: EntitySettings,
  errors: List<FieldError>,
  onSave: (EntitySettings) -> Unit,
  onFindSensors: suspend (prefix: String, stillCurrent: () -> Boolean) -> SensorSearch,
  modifier: Modifier = Modifier,
  initiallyExpanded: Boolean = false,
) {
  var draft by rememberSaveable(entities, stateSaver = draftSaver(entities)) { mutableStateOf(entities) }

  // Fill's question to Home Assistant: running, and the note under the list (spec 0004, 5.4).
  val scope = rememberCoroutineScope()
  var searching by remember { mutableStateOf(false) }
  var note by remember { mutableStateOf<SensorSearch?>(null) }

  val summary =
    when {
      entities.prefix.isNotBlank() -> entities.prefix
      entities.charge.isNotBlank() -> stringResource(R.string.entities_summary_manual)
      else -> stringResource(R.string.summary_not_set)
    }

  val prefixOk = Validation.isPrefixValid(draft.prefix.trim().trimEnd('_'))
  // While typing: flag a bad prefix at once. An error from the last save still wins.
  val liveError =
    if (draft.prefix.isNotEmpty() && !prefixOk) FieldError(Field.PREFIX, Problem.INVALID) else null

  SectionCard(R.string.entities_title, summary, initiallyExpanded, modifier) {
    SettingsTextField(
      value = draft.prefix,
      onValueChange = {
        draft = draft.copy(prefix = it)
        note = null
      },
      label = stringResource(R.string.entities_prefix),
      error = errors.errorFor(Field.PREFIX) ?: liveError,
      hint = stringResource(R.string.entities_fill_hint),
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
      OutlinedButton(
        onClick = {
          draft = EntityFill.fromPrefix(draft.prefix, draft)
          note = null
          val asked = draft.prefix
          val filled = draft.temperatures
          searching = true
          scope.launch {
            try {
              val found =
                onFindSensors(asked) { SensorFill.isCurrent(draft, asked, filled) }
              val outcome = SensorFill.apply(draft, asked, filled, found)
              draft = outcome.draft
              note = outcome.note
            } finally {
              searching = false
            }
          }
        },
        enabled = prefixOk && !searching,
      ) {
        Text(stringResource(R.string.entities_fill))
      }
      if (searching) {
        Spacer(Modifier.width(8.dp))
        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
      }
    }

    EntityField(R.string.entity_charge, draft.charge, errors.errorFor(Field.CHARGE)) {
      draft = draft.copy(charge = it)
    }
    EntityField(R.string.entity_total_voltage, draft.totalVoltage, errors.errorFor(Field.TOTAL_VOLTAGE)) {
      draft = draft.copy(totalVoltage = it)
    }
    EntityField(
      R.string.entity_current,
      draft.current,
      errors.errorFor(Field.CURRENT),
      hint = stringResource(R.string.entity_current_hint),
    ) {
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
      R.string.entity_total_capacity,
      draft.totalCapacity,
      errors.errorFor(Field.TOTAL_CAPACITY),
    ) {
      draft = draft.copy(totalCapacity = it)
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
      onChange = {
        draft = draft.copy(temperatures = it)
        note = null
      },
      note = note,
    )

    SaveButton(enabled = !searching) { onSave(draft) }
  }
}

@Composable
private fun TemperatureList(
  temperatures: List<String>,
  errors: List<FieldError>,
  onChange: (List<String>) -> Unit,
  note: SensorSearch?,
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
  val noteText =
    when (note) {
      SensorSearch.NoneFound -> stringResource(R.string.entities_sensors_none)
      is SensorSearch.Failed ->
        stringResource(R.string.entities_sensors_failed, Formatters(LocalContext.current).error(note.error))
      else -> null
    }
  noteText?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
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
  hint: String? = null,
  onValueChange: (String) -> Unit,
) {
  SettingsTextField(value, onValueChange, stringResource(label), error, hint)
}
