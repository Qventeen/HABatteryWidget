package io.lumensync.habattery.ui

import io.lumensync.habattery.domain.BatteryOverview
import io.lumensync.habattery.domain.FieldError
import io.lumensync.habattery.domain.WidgetModel
import io.lumensync.habattery.domain.model.Settings
import io.lumensync.habattery.domain.model.SyncError
import io.lumensync.habattery.domain.model.TokenType

/** Everything the main screen shows. It never holds the token. */
data class MainUiState(
  val settings: Settings = Settings(),
  val hasToken: Boolean = false,
  val overview: BatteryOverview? = null,
  /** What the widget preview draws; built with [overview], null until the first state. */
  val widget: WidgetModel? = null,
  val syncing: Boolean = false,
  val errors: List<FieldError> = emptyList(),
) {
  /**
   * HA rejects a non-admin token on the template path, so a 401 in Admin mode may mean the wrong
   * type. In User mode a 401 means the token itself is wrong: no hint.
   */
  val showTokenTypeHint: Boolean
    get() =
      overview?.error == SyncError.Unauthorized &&
        settings.connection.tokenType == TokenType.ADMIN
}
