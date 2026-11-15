package io.lumensync.habattery.data.ha

import io.lumensync.habattery.domain.model.EntitySettings

/** Builds the Jinja template that HA renders into one JSON object. Missing entities become `none`. */
object TemplateBuilder {
  fun build(entities: EntitySettings): String =
    "{{ {\"soc\": ${stateOrNone(entities.charge)}, " +
      "\"total_v\": ${stateOrNone(entities.totalVoltage)}, " +
      "\"current\": ${stateOrNone(entities.current)}, " +
      "\"power\": ${stateOrNone(entities.power)}, " +
      "\"ah_left\": ${stateOrNone(entities.capacityRemaining)}, " +
      "\"capacity\": ${stateOrNone(entities.totalCapacity)}, " +
      "\"avg_cell\": ${stateOrNone(entities.averageCellVoltage)}, " +
      "\"delta\": ${stateOrNone(entities.deltaCellVoltage)}, " +
      "\"mosfet\": ${stateOrNone(entities.mosfetTemperature)}, " +
      "\"temps\": [${entities.temperatures.joinToString(", ") { state(it) }}], " +
      "\"balancing\": ${rawStateOrNone(entities.balancing)}} | to_json }}"

  private fun state(entityId: String) = "states(${quote(entityId)}) | float(none)"

  private fun stateOrNone(entityId: String) = if (entityId.isBlank()) "none" else state(entityId)

  private fun rawStateOrNone(entityId: String) =
    if (entityId.isBlank()) "none" else "states(${quote(entityId)})"

  internal fun quote(value: String): String {
    val body = value.filterNot { it == '\r' || it == '\n' }.replace("\\", "\\\\").replace("'", "\\'")
    return "'$body'"
  }
}
