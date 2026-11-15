package io.lumensync.habattery.domain

import io.lumensync.habattery.domain.model.ConnectionSettings
import io.lumensync.habattery.domain.model.EntitySettings
import io.lumensync.habattery.domain.model.RefreshSettings
import io.lumensync.habattery.domain.model.Settings

enum class Field {
  PRIMARY_URL,
  FALLBACK_URL,
  TOKEN,
  PREFIX,
  CHARGE,
  POWER,
  CURRENT,
  TOTAL_VOLTAGE,
  CAPACITY_REMAINING,
  AVERAGE_CELL_VOLTAGE,
  DELTA_CELL_VOLTAGE,
  MOSFET_TEMPERATURE,
  TEMPERATURES,
  BALANCING,
  INTERVAL,
  CAPACITY,
}

enum class Problem {
  REQUIRED,
  INVALID,
  INSECURE,
  OUT_OF_RANGE,
  TOO_MANY,
}

data class FieldError(val field: Field, val problem: Problem, val index: Int? = null)

/** Pure validation of already-trimmed settings values. */
object Validation {
  private val PREFIX = Regex("^[a-z0-9_]+$")
  private val ENTITY_ID = Regex("^[a-zA-Z0-9_]+\\.[a-zA-Z0-9_]+$")
  const val MAX_CAPACITY_AH = 10_000.0

  fun connection(c: ConnectionSettings): List<FieldError> =
    listOfNotNull(
      urlError(Field.PRIMARY_URL, c.primaryUrl, required = true),
      urlError(Field.FALLBACK_URL, c.fallbackUrl, required = false),
    )

  fun isPrefixValid(prefix: String): Boolean = PREFIX.matches(prefix.trim())

  fun entities(e: EntitySettings): List<FieldError> =
    listOfNotNull(
      if (e.prefix.isNotBlank() && !isPrefixValid(e.prefix)) FieldError(Field.PREFIX, Problem.INVALID)
      else null,
      entityError(Field.CHARGE, e.charge, required = true),
      entityError(Field.TOTAL_VOLTAGE, e.totalVoltage, required = false),
      entityError(Field.CURRENT, e.current, required = false),
      entityError(Field.POWER, e.power, required = false),
      entityError(Field.CAPACITY_REMAINING, e.capacityRemaining, required = false),
      entityError(Field.AVERAGE_CELL_VOLTAGE, e.averageCellVoltage, required = false),
      entityError(Field.DELTA_CELL_VOLTAGE, e.deltaCellVoltage, required = false),
      entityError(Field.MOSFET_TEMPERATURE, e.mosfetTemperature, required = false),
    ) +
      e.temperatures.mapIndexedNotNull { i, id ->
        entityError(Field.TEMPERATURES, id, required = true, index = i)
      } +
      listOfNotNull(
        if (e.temperatures.size > EntitySettings.MAX_TEMPERATURES)
          FieldError(Field.TEMPERATURES, Problem.TOO_MANY)
        else null,
        entityError(Field.BALANCING, e.balancing, required = false),
      )

  fun refresh(r: RefreshSettings): List<FieldError> {
    if (!r.auto) return emptyList()
    val minutes = r.intervalMinutes ?: return listOf(FieldError(Field.INTERVAL, Problem.REQUIRED))
    return if (minutes in RefreshSettings.MIN_INTERVAL..RefreshSettings.MAX_INTERVAL) emptyList()
    else listOf(FieldError(Field.INTERVAL, Problem.OUT_OF_RANGE))
  }

  fun capacity(ah: Double?): List<FieldError> {
    if (ah == null) return emptyList()
    return if (ah > 0.0 && ah <= MAX_CAPACITY_AH) emptyList()
    else listOf(FieldError(Field.CAPACITY, Problem.OUT_OF_RANGE))
  }

  fun isConfigured(s: Settings, hasToken: Boolean): Boolean =
    hasToken && s.connection.primaryUrl.isNotBlank() && s.entities.charge.isNotBlank()

  private fun urlError(field: Field, url: String, required: Boolean): FieldError? =
    when {
      url.isBlank() -> if (required) FieldError(field, Problem.REQUIRED) else null
      else ->
        when (UrlPolicy.check(url)) {
          UrlPolicy.Verdict.OK -> null
          UrlPolicy.Verdict.INVALID -> FieldError(field, Problem.INVALID)
          UrlPolicy.Verdict.INSECURE -> FieldError(field, Problem.INSECURE)
        }
    }

  private fun entityError(field: Field, id: String, required: Boolean, index: Int? = null): FieldError? =
    when {
      id.isBlank() -> if (required) FieldError(field, Problem.REQUIRED, index) else null
      !ENTITY_ID.matches(id) -> FieldError(field, Problem.INVALID, index)
      else -> null
    }
}
