package io.lumensync.habattery.ui

import io.lumensync.habattery.domain.model.SyncError

/** The result of Fill's question to Home Assistant: what the Entities section does with it. */
sealed interface SensorSearch {
  /** No URL or token is saved: no request was made. */
  data object Skipped : SensorSearch

  data class Found(val ids: List<String>) : SensorSearch

  data object NoneFound : SensorSearch

  data class Failed(val error: SyncError) : SensorSearch

  /** The prefix in the draft changed while the request ran: drop the result. */
  data object Superseded : SensorSearch
}
