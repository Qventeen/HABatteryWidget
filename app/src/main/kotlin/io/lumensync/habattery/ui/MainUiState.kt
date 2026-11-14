package io.lumensync.habattery.ui

import io.lumensync.habattery.domain.BatteryOverview
import io.lumensync.habattery.domain.FieldError
import io.lumensync.habattery.domain.model.Settings

/** Everything the main screen shows. It never holds the token. */
data class MainUiState(
  val settings: Settings = Settings(),
  val hasToken: Boolean = false,
  val overview: BatteryOverview? = null,
  val syncing: Boolean = false,
  val errors: List<FieldError> = emptyList(),
)
