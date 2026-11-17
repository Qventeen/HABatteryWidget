package io.lumensync.habattery.domain

import io.lumensync.habattery.domain.model.EntitySettings

/** Fills every entity id from the BMS prefix. Hand edits are overwritten. */
object EntityFill {
  fun fromPrefix(prefix: String, current: EntitySettings): EntitySettings {
    val p = prefix.trim().trimEnd('_')
    return current.copy(
      prefix = p,
      charge = "sensor.${p}_state_of_charge",
      totalVoltage = "sensor.${p}_total_voltage",
      current = "sensor.${p}_current",
      power = "sensor.${p}_power",
      capacityRemaining = "sensor.${p}_capacity_remaining",
      totalCapacity = "sensor.${p}_total_battery_capacity",
      averageCellVoltage = "sensor.${p}_average_cell_voltage",
      deltaCellVoltage = "sensor.${p}_delta_cell_voltage",
      mosfetTemperature = "sensor.${p}_power_tube_temperature",
      temperatures = listOf("sensor.${p}_temperature_sensor_1"),
      balancing = "sensor.${p}_balancing",
    )
  }
}
