package io.lumensync.habattery.ui.sections

import androidx.annotation.StringRes
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import io.lumensync.habattery.domain.FieldError
import io.lumensync.habattery.domain.model.EntitySettings

/** Saves the draft as a flat list: the five single entities, then the cells. */
private val DraftSaver =
  listSaver<EntitySettings, String>(
    save = { listOf(it.charge, it.power, it.fallbackPower, it.current, it.totalVoltage) + it.cells },
    restore = { EntitySettings(it[0], it[1], it[2], it[3], it[4], it.drop(SINGLE_FIELDS)) },
  )

private const val SINGLE_FIELDS = 5

/** Entity IDs: charge, power, fallback power, current, total voltage and up to 32 cells. */
@Composable
fun EntitiesSection(
  entities: EntitySettings,
  errors: List<FieldError>,
  onSave: (EntitySettings) -> Unit,
  modifier: Modifier = Modifier,
) {
  var draft by rememberSaveable(entities, stateSaver = DraftSaver) { mutableStateOf(entities) }

  SectionCard(R.string.entities_title, modifier) {
    EntityField(R.string.entity_charge, draft.charge, errors.errorFor(Field.CHARGE)) {
      draft = draft.copy(charge = it)
    }
    EntityField(R.string.entity_power, draft.power, errors.errorFor(Field.POWER)) {
      draft = draft.copy(power = it)
    }
    EntityField(
      R.string.entity_fallback_power,
      draft.fallbackPower,
      errors.errorFor(Field.FALLBACK_POWER),
    ) {
      draft = draft.copy(fallbackPower = it)
    }
    EntityField(R.string.entity_current, draft.current, errors.errorFor(Field.CURRENT)) {
      draft = draft.copy(current = it)
    }
    EntityField(
      R.string.entity_total_voltage,
      draft.totalVoltage,
      errors.errorFor(Field.TOTAL_VOLTAGE),
    ) {
      draft = draft.copy(totalVoltage = it)
    }

    Text(stringResource(R.string.entity_cells), style = MaterialTheme.typography.titleSmall)
    draft.cells.forEachIndexed { index, cell ->
      SettingsTextField(
        value = cell,
        onValueChange = { value ->
          draft = draft.copy(cells = draft.cells.toMutableList().apply { set(index, value) })
        },
        label = stringResource(R.string.entity_cell, index + 1),
        error = errors.errorFor(Field.CELLS, cellIndex = index),
        trailingIcon = {
          IconButton(
            onClick = {
              draft = draft.copy(cells = draft.cells.toMutableList().apply { removeAt(index) })
            }
          ) {
            Icon(
              painter = painterResource(R.drawable.ic_close),
              contentDescription = stringResource(R.string.entity_cell_remove, index + 1),
            )
          }
        },
      )
    }
    errors.errorFor(Field.CELLS)?.let { error ->
      Text(
        fieldErrorText(error),
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodySmall,
      )
    }
    TextButton(
      onClick = { draft = draft.copy(cells = draft.cells + "") },
      enabled = draft.cells.size < EntitySettings.MAX_CELLS,
    ) {
      Text(stringResource(R.string.entity_cell_add))
    }

    SaveButton { onSave(draft) }
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
