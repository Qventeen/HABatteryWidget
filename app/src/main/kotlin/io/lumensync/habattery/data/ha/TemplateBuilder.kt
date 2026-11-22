package io.lumensync.habattery.data.ha

import io.lumensync.habattery.domain.model.EntitySettings
import io.lumensync.habattery.domain.model.FetchRequest
import io.lumensync.habattery.domain.model.InverterEntitySettings

/**
 * Builds the Jinja template that HA renders into one JSON object. Missing entities become `none`;
 * a part not asked has every key, all `none`.
 */
object TemplateBuilder {
  fun build(request: FetchRequest): String {
    val battery = request.battery ?: EntitySettings()
    val inverter = request.inverter ?: InverterEntitySettings()
    return "{{ {\"soc\": ${stateOrNone(battery.charge)}, " +
      "\"total_v\": ${stateOrNone(battery.totalVoltage)}, " +
      "\"current\": ${stateOrNone(battery.current)}, " +
      "\"power\": ${stateOrNone(battery.power)}, " +
      "\"ah_left\": ${stateOrNone(battery.capacityRemaining)}, " +
      "\"capacity\": ${stateOrNone(battery.totalCapacity)}, " +
      "\"avg_cell\": ${stateOrNone(battery.averageCellVoltage)}, " +
      "\"delta\": ${stateOrNone(battery.deltaCellVoltage)}, " +
      "\"mosfet\": ${stateOrNone(battery.mosfetTemperature)}, " +
      "\"temps\": [${battery.temperatures.joinToString(", ") { state(it) }}], " +
      "\"balancing\": ${rawStateOrNone(battery.balancing)}, " +
      "\"inv_mode\": ${rawStateOrNone(inverter.mode)}, " +
      "\"inv_grid_v\": ${stateOrNone(inverter.gridVoltage)}, " +
      "\"inv_out_v\": ${stateOrNone(inverter.outputVoltage)}} | to_json }}"
  }

  /**
   * Lists the entity ids that match `sensor.<prefix>_temperature_sensor_<digits>`. The prefix must be
   * an object-id prefix (`a-z`, `0-9`, `_`), so no user text reaches the template code. `[.]` keeps
   * the pattern free of backslashes.
   */
  fun temperatureSensors(prefix: String): String {
    require(isObjectIdPrefix(prefix)) { "prefix" }
    return "{{ states.sensor | map(attribute='entity_id') | " +
      "select('match', '${TemperatureSensors.pattern(prefix)}') | list | to_json }}"
  }

  fun isObjectIdPrefix(prefix: String): Boolean = OBJECT_ID.matches(prefix)

  private val OBJECT_ID = Regex("[a-z0-9_]+")

  private fun state(entityId: String) = "states(${quote(entityId)}) | float(none)"

  private fun stateOrNone(entityId: String) = if (entityId.isBlank()) "none" else state(entityId)

  private fun rawStateOrNone(entityId: String) =
    if (entityId.isBlank()) "none" else "states(${quote(entityId)})"

  internal fun quote(value: String): String {
    val body = value.filterNot { it == '\r' || it == '\n' }.replace("\\", "\\\\").replace("'", "\\'")
    return "'$body'"
  }
}
