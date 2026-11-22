package io.lumensync.habattery.ui.sections

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.lumensync.habattery.R
import io.lumensync.habattery.domain.Field
import io.lumensync.habattery.domain.FieldError
import io.lumensync.habattery.domain.Problem
import io.lumensync.habattery.domain.Validation
import io.lumensync.habattery.domain.model.EntitySettings
import io.lumensync.habattery.domain.model.MAX_TEXT_LEVELS
import io.lumensync.habattery.domain.model.RefreshSettings

/** The text shown under an input for a validation error. */
@Composable
internal fun fieldErrorText(error: FieldError): String {
  if (error.field == Field.TEXT_LEVEL_TEXTS) return textLevelText(error.problem)
  return when (error.problem) {
    Problem.REQUIRED -> stringResource(R.string.field_required)
    Problem.INVALID -> invalidText(error.field)
    Problem.DUPLICATE -> stringResource(R.string.field_level_edge_duplicate)
    Problem.OVERLAP -> stringResource(R.string.field_level_edge_overlap)
    Problem.TOO_MANY ->
      stringResource(R.string.field_temperatures_too_many, EntitySettings.MAX_TEMPERATURES)
    // Only the interval, the cells and the capacity have a range.
    Problem.OUT_OF_RANGE ->
      when (error.field) {
        Field.INTERVAL ->
          stringResource(
            R.string.refresh_interval_range,
            RefreshSettings.MIN_INTERVAL,
            RefreshSettings.MAX_INTERVAL,
          )
        Field.CELLS -> cellsText()
        else -> stringResource(R.string.field_capacity_range, Validation.MAX_CAPACITY_AH.toInt())
      }
  }
}

@Composable
private fun invalidText(field: Field): String =
  when (field) {
    Field.URL -> stringResource(R.string.field_url_invalid)
    // The token text is never checked; INVALID means the Keystore failed to store it.
    Field.TOKEN -> stringResource(R.string.field_token_not_saved)
    Field.CHARGE,
    Field.POWER,
    Field.CURRENT,
    Field.TOTAL_VOLTAGE,
    Field.CAPACITY_REMAINING,
    Field.TOTAL_CAPACITY,
    Field.AVERAGE_CELL_VOLTAGE,
    Field.DELTA_CELL_VOLTAGE,
    Field.MOSFET_TEMPERATURE,
    Field.TEMPERATURES,
    Field.BALANCING,
    Field.INVERTER_MODE,
    Field.INVERTER_GRID,
    Field.INVERTER_OUTPUT -> stringResource(R.string.field_entity_invalid)
    Field.PREFIX,
    Field.INVERTER_PREFIX -> stringResource(R.string.field_prefix_invalid)
    Field.INTERVAL -> stringResource(R.string.field_interval_invalid)
    Field.CAPACITY -> stringResource(R.string.field_capacity_invalid)
    Field.CELLS -> cellsText()
    Field.LEVEL_EDGE -> stringResource(R.string.field_level_edge_invalid)
    Field.LEVEL_MARGIN -> stringResource(R.string.field_level_margin_invalid)
    Field.TEXT_LEVEL_TEXTS -> stringResource(R.string.field_mode_text_invalid)
  }

/** A mode's texts: the messages name texts and modes, not levels or sensors. */
@Composable
private fun textLevelText(problem: Problem): String =
  when (problem) {
    Problem.REQUIRED -> stringResource(R.string.field_mode_texts_required)
    Problem.DUPLICATE -> stringResource(R.string.field_mode_text_duplicate)
    Problem.TOO_MANY -> stringResource(R.string.field_modes_too_many, MAX_TEXT_LEVELS)
    else -> stringResource(R.string.field_mode_text_invalid)
  }

/** Not a whole number and out of range share one text: it names both rules. */
@Composable
private fun cellsText(): String = stringResource(R.string.field_cells_range, Validation.MAX_CELLS)
