package io.lumensync.habattery.ui

import io.lumensync.habattery.domain.BatteryOverview
import io.lumensync.habattery.domain.FieldError
import io.lumensync.habattery.domain.InverterWidgetModel
import io.lumensync.habattery.domain.WidgetModel
import io.lumensync.habattery.domain.model.Settings
import io.lumensync.habattery.domain.model.SyncError
import io.lumensync.habattery.domain.model.TokenType

/** Everything the main screen shows. It never holds the token. */
data class MainUiState(
  val settings: Settings = Settings(),
  /** The selected token type's slot holds a token; the other slot does not count. */
  val hasToken: Boolean = false,
  val overview: BatteryOverview? = null,
  /** What the widget preview draws; built with [overview], null until the first state. */
  val widget: WidgetModel? = null,
  /** What the strip preview draws; null until the first state. */
  val inverter: InverterWidgetModel? = null,
  val syncing: Boolean = false,
  val errors: List<FieldError> = emptyList(),
) {
  /**
   * HA rejects a non-admin token on the template path, so a 401 in Admin mode may mean the wrong
   * type. Either part's error counts: when one part is not configured, only the other can carry
   * the 401. In User mode a 401 means the token itself is wrong: no hint.
   */
  val showTokenTypeHint: Boolean
    get() =
      SyncError.Unauthorized in listOf(overview?.error, inverter?.error) &&
        settings.connection.tokenType == TokenType.ADMIN

  /**
   * No URL, or no token of the selected type: both widgets need these, so the app opens on Common
   * and unfolds Connection and Token. The entities do not count; each widget tab has its own.
   */
  val connectionMissing: Boolean
    get() = settings.connection.url.isBlank() || !hasToken
}

