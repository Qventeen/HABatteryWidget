package io.lumensync.habattery.ui.sections

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.lumensync.habattery.R
import io.lumensync.habattery.domain.Field
import io.lumensync.habattery.domain.FieldError
import io.lumensync.habattery.domain.model.EntitySettings

/** Saves the draft as a flat list of the four entity IDs. */
private val DraftSaver =
  listSaver<EntitySettings, String>(
    save = { listOf(it.charge, it.power, it.current, it.totalVoltage) },
    restore = { EntitySettings(it[0], it[1], it[2], it[3]) },
  )

/** Entity IDs: charge, power, current and total voltage. */
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
