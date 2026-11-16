package io.lumensync.habattery.ui

import io.lumensync.habattery.domain.model.EntitySettings

/** What the Entities draft looks like after Fill's answer from Home Assistant (spec 0004, 5.4). */
data class FillOutcome(val draft: EntitySettings, val note: SensorSearch?)

object SensorFill {
  /**
   * The answer still belongs to the draft only if the prefix is the one asked and the temperature
   * list is still the one Fill set: edits the user made while the request ran win.
   */
  fun isCurrent(draft: EntitySettings, askedPrefix: String, filledList: List<String>): Boolean =
    draft.prefix == askedPrefix && draft.temperatures == filledList

  fun apply(
    draft: EntitySettings,
    askedPrefix: String,
    filledList: List<String>,
    result: SensorSearch,
  ): FillOutcome =
    when {
      !isCurrent(draft, askedPrefix, filledList) -> FillOutcome(draft, null)
      result is SensorSearch.Found -> FillOutcome(draft.copy(temperatures = result.ids), null)
      result is SensorSearch.NoneFound || result is SensorSearch.Failed -> FillOutcome(draft, result)
      else -> FillOutcome(draft, null)
    }
}
