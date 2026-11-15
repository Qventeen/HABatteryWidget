package io.lumensync.habattery.data.ha

import io.lumensync.habattery.domain.model.EntitySettings

/** Builds the Jinja template that HA renders into one JSON object. Missing entities become `none`. */
object TemplateBuilder {
  fun build(entities: EntitySettings): String =
    "{{ {\"soc\": ${stateOrNone(entities.charge)}, " +
      "\"power\": ${stateOrNone(entities.power)}, " +
      "\"current\": ${stateOrNone(entities.current)}, " +
      "\"total_v\": ${stateOrNone(entities.totalVoltage)}} | to_json }}"

  private fun state(entityId: String) = "states(${quote(entityId)}) | float(none)"

  private fun stateOrNone(entityId: String) = if (entityId.isBlank()) "none" else state(entityId)

  internal fun quote(value: String): String {
    val body = value.filterNot { it == '\r' || it == '\n' }.replace("\\", "\\\\").replace("'", "\\'")
    return "'$body'"
  }
}
