package io.lumensync.habattery.domain

import io.lumensync.habattery.domain.model.ConnectionSettings
import io.lumensync.habattery.domain.model.EntitySettings
import io.lumensync.habattery.domain.model.RefreshSettings
import io.lumensync.habattery.domain.model.Settings

enum class Field {
  PRIMARY_URL,
  FALLBACK_URL,
  TOKEN,
  CHARGE,
  POWER,
  FALLBACK_POWER,
  CURRENT,
  TOTAL_VOLTAGE,
  CELLS,
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

data class FieldError(val field: Field, val problem: Problem, val cellIndex: Int? = null)

/** Pure validation of already-trimmed settings values. */
object Validation {
  private val ENTITY_ID = Regex("^[a-zA-Z0-9_]+\\.[a-zA-Z0-9_]+$")
  const val MAX_CAPACITY_AH = 10_000.0

  fun connection(c: ConnectionSettings): List<FieldError> =
    listOfNotNull(
      urlError(Field.PRIMARY_URL, c.primaryUrl, required = true),
      urlError(Field.FALLBACK_URL, c.fallbackUrl, required = false),
    )

  fun entities(e: EntitySettings): List<FieldError> {
    val errors =
      listOfNotNull(
        entityError(Field.CHARGE, e.charge, required = true),
        entityError(Field.POWER, e.power, required = false),
        entityError(Field.FALLBACK_POWER, e.fallbackPower, required = false),
        entityError(Field.CURRENT, e.current, required = false),
        entityError(Field.TOTAL_VOLTAGE, e.totalVoltage, required = false),
      )
    val cellErrors =
      e.cells.mapIndexedNotNull { i, id -> entityError(Field.CELLS, id, required = true, i) }
    val tooMany =
      if (e.cells.size > EntitySettings.MAX_CELLS) listOf(FieldError(Field.CELLS, Problem.TOO_MANY))
      else emptyList()
    return errors + cellErrors + tooMany
  }

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

  private fun entityError(
    field: Field,
    id: String,
    required: Boolean,
    cellIndex: Int? = null,
  ): FieldError? =
    when {
      id.isBlank() -> if (required) FieldError(field, Problem.REQUIRED, cellIndex) else null
      !ENTITY_ID.matches(id) -> FieldError(field, Problem.INVALID, cellIndex)
      else -> null
    }
}
