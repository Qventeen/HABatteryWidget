package io.lumensync.habattery.domain

import io.lumensync.habattery.domain.model.ConnectionSettings
import io.lumensync.habattery.domain.model.ColorLevel
import io.lumensync.habattery.domain.model.EntitySettings
import io.lumensync.habattery.domain.model.InverterEntitySettings
import io.lumensync.habattery.domain.model.LevelSide
import io.lumensync.habattery.domain.model.LevelValue
import io.lumensync.habattery.domain.model.MAX_LEVELS
import io.lumensync.habattery.domain.model.RefreshSettings
import io.lumensync.habattery.domain.model.Settings

enum class Field {
  URL,
  TOKEN,
  PREFIX,
  CHARGE,
  POWER,
  CURRENT,
  TOTAL_VOLTAGE,
  CAPACITY_REMAINING,
  TOTAL_CAPACITY,
  AVERAGE_CELL_VOLTAGE,
  DELTA_CELL_VOLTAGE,
  MOSFET_TEMPERATURE,
  TEMPERATURES,
  BALANCING,
  INTERVAL,
  CAPACITY,
  CELLS,
  LEVEL_EDGE,
  LEVEL_MARGIN,
  INVERTER_PREFIX,
  INVERTER_MODE,
  INVERTER_GRID,
  INVERTER_OUTPUT,
}

enum class Problem {
  REQUIRED,
  INVALID,
  OUT_OF_RANGE,
  TOO_MANY,
  DUPLICATE,
  OVERLAP,
}

data class FieldError(val field: Field, val problem: Problem, val index: Int? = null)

/** Pure validation of already-trimmed settings values. */
object Validation {
  private val PREFIX = Regex("^[a-z0-9_]+$")
  private val ENTITY_ID = Regex("^[a-zA-Z0-9_]+\\.[a-zA-Z0-9_]+$")
  const val MAX_CAPACITY_AH = 10_000.0

  /** JK-BMS boards take up to 24 cells; 32 leaves room for other boards. */
  const val MAX_CELLS = 32

  fun connection(c: ConnectionSettings): List<FieldError> =
    listOfNotNull(urlError(c.url))

  fun isPrefixValid(prefix: String): Boolean = PREFIX.matches(prefix.trim())

  fun isEntityId(id: String): Boolean = ENTITY_ID.matches(id)

  fun entities(e: EntitySettings): List<FieldError> =
    listOfNotNull(
      if (e.prefix.isNotBlank() && !isPrefixValid(e.prefix)) FieldError(Field.PREFIX, Problem.INVALID)
      else null,
      entityError(Field.CHARGE, e.charge, required = true),
      entityError(Field.TOTAL_VOLTAGE, e.totalVoltage, required = false),
      entityError(Field.CURRENT, e.current, required = false),
      entityError(Field.POWER, e.power, required = false),
      entityError(Field.CAPACITY_REMAINING, e.capacityRemaining, required = false),
      entityError(Field.TOTAL_CAPACITY, e.totalCapacity, required = false),
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

  /** All three ids blank is "no inverter" (no error); a partly filled set needs the blank ones. */
  fun inverterEntities(e: InverterEntitySettings): List<FieldError> {
    val noneSet = e.mode.isBlank() && e.gridVoltage.isBlank() && e.outputVoltage.isBlank()
    return listOfNotNull(
      if (e.prefix.isNotBlank() && !isPrefixValid(e.prefix)) FieldError(Field.INVERTER_PREFIX, Problem.INVALID)
      else null,
      entityError(Field.INVERTER_MODE, e.mode, required = !noneSet),
      entityError(Field.INVERTER_GRID, e.gridVoltage, required = !noneSet),
      entityError(Field.INVERTER_OUTPUT, e.outputVoltage, required = !noneSet),
    )
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

  fun cells(count: Int?): List<FieldError> =
    if (count == null || count in 1..MAX_CELLS) emptyList()
    else listOf(FieldError(Field.CELLS, Problem.OUT_OF_RANGE))

  /**
   * Checks the levels of one [value]. At most one error per index, the first found: a missing or
   * stray edge, the same edge twice on one side, or a ↓ and an ↑ edge less than two steps apart
   * (the middle level would be empty). Errors with no index: more than [MAX_LEVELS] levels, and a
   * list without exactly one middle level.
   */
  fun levels(value: LevelValue, levels: List<ColorLevel>): List<FieldError> {
    val errors = mutableListOf<FieldError>()
    if (levels.size > MAX_LEVELS) errors += FieldError(Field.LEVEL_EDGE, Problem.TOO_MANY)
    if (levels.count { it.side == LevelSide.MIDDLE } != 1) {
      errors += FieldError(Field.LEVEL_EDGE, Problem.INVALID)
    }
    val atIndex = mutableMapOf<Int, Problem>()
    val seen = mutableSetOf<Pair<LevelSide, Long>>()
    levels.forEachIndexed { i, level ->
      val edge = level.edge
      when {
        level.side == LevelSide.MIDDLE -> if (edge != null) atIndex[i] = Problem.INVALID
        edge == null || !edge.isFinite() -> atIndex[i] = Problem.REQUIRED
        !seen.add(level.side to edge.toSteps(value.decimals)) -> atIndex[i] = Problem.DUPLICATE
      }
    }
    for ((i, below) in levels.withIndex()) {
      if (below.side != LevelSide.BELOW || below.edge == null || !below.edge.isFinite()) continue
      for ((k, above) in levels.withIndex()) {
        if (above.side != LevelSide.ABOVE || above.edge == null || !above.edge.isFinite()) continue
        val gap = above.edge.toSteps(value.decimals) - below.edge.toSteps(value.decimals)
        if (gap < 2) {
          atIndex.putIfAbsent(i, Problem.OVERLAP)
          atIndex.putIfAbsent(k, Problem.OVERLAP)
        }
      }
    }
    return errors + atIndex.toSortedMap().map { (i, problem) -> FieldError(Field.LEVEL_EDGE, problem, i) }
  }

  fun isBatteryConfigured(s: Settings, hasToken: Boolean): Boolean =
    hasToken && s.connection.url.isNotBlank() && s.entities.charge.isNotBlank()

  fun isInverterConfigured(s: Settings, hasToken: Boolean): Boolean =
    hasToken &&
      s.connection.url.isNotBlank() &&
      s.inverter.entities.let {
        it.mode.isNotBlank() && it.gridVoltage.isNotBlank() && it.outputVoltage.isNotBlank()
      }

  private fun urlError(url: String): FieldError? =
    when {
      url.isBlank() -> FieldError(Field.URL, Problem.REQUIRED)
      UrlPolicy.check(url) == UrlPolicy.Verdict.INVALID -> FieldError(Field.URL, Problem.INVALID)
      else -> null
    }

  private fun entityError(field: Field, id: String, required: Boolean, index: Int? = null): FieldError? =
    when {
      id.isBlank() -> if (required) FieldError(field, Problem.REQUIRED, index) else null
      !ENTITY_ID.matches(id) -> FieldError(field, Problem.INVALID, index)
      else -> null
    }
}
