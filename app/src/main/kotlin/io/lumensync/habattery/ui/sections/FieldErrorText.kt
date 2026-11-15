package io.lumensync.habattery.ui.sections

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.lumensync.habattery.R
import io.lumensync.habattery.domain.Field
import io.lumensync.habattery.domain.FieldError
import io.lumensync.habattery.domain.Problem
import io.lumensync.habattery.domain.Validation
import io.lumensync.habattery.domain.model.EntitySettings
import io.lumensync.habattery.domain.model.RefreshSettings

/** The text shown under an input for a validation error. */
@Composable
internal fun fieldErrorText(error: FieldError): String =
  when (error.problem) {
    Problem.REQUIRED -> stringResource(R.string.field_required)
    Problem.INVALID -> stringResource(invalidMessage(error.field))
    Problem.TOO_MANY ->
      stringResource(R.string.field_temperatures_too_many, EntitySettings.MAX_TEMPERATURES)
    // Only the interval and the capacity have a range.
    Problem.OUT_OF_RANGE ->
      if (error.field == Field.INTERVAL) {
        stringResource(
          R.string.refresh_interval_range,
          RefreshSettings.MIN_INTERVAL,
          RefreshSettings.MAX_INTERVAL,
        )
      } else {
        stringResource(R.string.field_capacity_range, Validation.MAX_CAPACITY_AH.toInt())
      }
  }

@StringRes
private fun invalidMessage(field: Field): Int =
  when (field) {
    Field.URL -> R.string.field_url_invalid
    // The token text is never checked; INVALID means the Keystore failed to store it.
    Field.TOKEN -> R.string.field_token_not_saved
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
    Field.BALANCING -> R.string.field_entity_invalid
    Field.PREFIX -> R.string.field_prefix_invalid
    Field.INTERVAL -> R.string.field_interval_invalid
    Field.CAPACITY -> R.string.field_capacity_invalid
  }
