package io.lumensync.habattery.ui.sections

import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.lumensync.habattery.R
import io.lumensync.habattery.domain.EntityFill
import io.lumensync.habattery.domain.Field
import io.lumensync.habattery.domain.FieldError
import io.lumensync.habattery.domain.Problem
import io.lumensync.habattery.domain.Validation
import io.lumensync.habattery.domain.model.InverterEntitySettings

/** Strings only, so the draft survives rotation and process death. */
private val DraftSaver =
  listSaver<InverterEntitySettings, String>(
    save = { listOf(it.prefix, it.mode, it.gridVoltage, it.outputVoltage) },
    restore = {
      InverterEntitySettings(it[0], it[1], it[2], it[3])
    },
  )

/**
 * The strip's entity ids: a prefix with Fill, then the three ids. Fill writes the three ids the
 * prefix gives; unlike the battery's Fill it asks Home Assistant nothing, as the strip has no
 * sensor list.
 */
@Composable
fun InverterEntitiesSection(
  entities: InverterEntitySettings,
  errors: List<FieldError>,
  onSave: (InverterEntitySettings) -> Unit,
  modifier: Modifier = Modifier,
  initiallyExpanded: Boolean = false,
) {
  var draft by rememberSaveable(entities, stateSaver = DraftSaver) { mutableStateOf(entities) }

  val summary =
    when {
      entities.prefix.isNotBlank() -> entities.prefix
      entities.mode.isNotBlank() -> stringResource(R.string.entities_summary_manual)
      else -> stringResource(R.string.summary_not_set)
    }

  val prefixOk = Validation.isPrefixValid(draft.prefix.trim().trimEnd('_'))
  // While typing: flag a bad prefix at once. An error from the last save still wins.
  val liveError =
    if (draft.prefix.isNotEmpty() && !prefixOk) {
      FieldError(Field.INVERTER_PREFIX, Problem.INVALID)
    } else {
      null
    }

  SectionCard(R.string.entities_title, summary, initiallyExpanded, modifier) {
    SettingsTextField(
      value = draft.prefix,
      onValueChange = { draft = draft.copy(prefix = it) },
      label = stringResource(R.string.inverter_entities_prefix),
      error = errors.errorFor(Field.INVERTER_PREFIX) ?: liveError,
      hint = stringResource(R.string.entities_fill_hint),
    )
    OutlinedButton(
      onClick = { draft = EntityFill.inverterFromPrefix(draft.prefix, draft) },
      enabled = prefixOk,
    ) {
      Text(stringResource(R.string.entities_fill))
    }
    SettingsTextField(
      value = draft.mode,
      onValueChange = { draft = draft.copy(mode = it) },
      label = stringResource(R.string.inverter_entity_mode),
      error = errors.errorFor(Field.INVERTER_MODE),
    )
    SettingsTextField(
      value = draft.gridVoltage,
      onValueChange = { draft = draft.copy(gridVoltage = it) },
      label = stringResource(R.string.inverter_entity_grid_voltage),
      error = errors.errorFor(Field.INVERTER_GRID),
    )
    SettingsTextField(
      value = draft.outputVoltage,
      onValueChange = { draft = draft.copy(outputVoltage = it) },
      label = stringResource(R.string.inverter_entity_output_voltage),
      error = errors.errorFor(Field.INVERTER_OUTPUT),
    )
    SaveButton { onSave(draft) }
  }
}
