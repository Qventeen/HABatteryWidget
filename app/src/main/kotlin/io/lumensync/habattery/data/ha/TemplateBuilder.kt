package io.lumensync.habattery.data.ha

import io.lumensync.habattery.domain.model.EntitySettings

/** Builds the Jinja template that HA renders into one JSON object. Missing entities become `none`. */
object TemplateBuilder {
  fun build(entities: EntitySettings): String {
    val usePrelude = entities.power.isNotBlank() && entities.fallbackPower.isNotBlank()
    val prelude =
      if (usePrelude) {
        "{% set p = ${state(entities.power)} %}" +
          "{% if p is none %}{% set p = ${state(entities.fallbackPower)} %}{% endif %}"
      } else {
        ""
      }
    val power =
      when {
        usePrelude -> "p"
        entities.power.isNotBlank() -> state(entities.power)
        entities.fallbackPower.isNotBlank() -> state(entities.fallbackPower)
        else -> "none"
      }
    val cells = entities.cells.joinToString(", ") { state(it) }
    return prelude +
      "{{ {\"soc\": ${stateOrNone(entities.charge)}, \"power\": $power, " +
      "\"current\": ${stateOrNone(entities.current)}, " +
      "\"total_v\": ${stateOrNone(entities.totalVoltage)}, " +
      "\"cells\": [$cells]} | to_json }}"
  }

  private fun state(entityId: String) = "states(${quote(entityId)}) | float(none)"

  private fun stateOrNone(entityId: String) = if (entityId.isBlank()) "none" else state(entityId)

  internal fun quote(value: String): String {
    val body = value.filterNot { it == '\r' || it == '\n' }.replace("\\", "\\\\").replace("'", "\\'")
    return "'$body'"
  }
}
