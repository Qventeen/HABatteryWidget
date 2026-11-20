package io.lumensync.habattery.domain

import io.lumensync.habattery.domain.model.BatteryReading
import io.lumensync.habattery.domain.model.ColorLevel
import io.lumensync.habattery.domain.model.CustomLevels
import io.lumensync.habattery.domain.model.LevelAlertState
import io.lumensync.habattery.domain.model.LevelValue
import io.lumensync.habattery.domain.model.Settings
import io.lumensync.habattery.domain.model.customLevels
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * One notification to post: [value] has entered [level]. [span] is what the level covers in its
 * list, since a middle level's range cannot be told from the level alone. [shown] is the number as
 * the widget shows it.
 */
data class LevelAlert(
  val value: LevelValue,
  val level: ColorLevel,
  val span: LevelSpan,
  val shown: Double,
  val measuredAtMillis: Long,
)

/** The alerts of one check and the new state of every value, keyed by [LevelValue.name]. */
data class AlertOutcome(val alerts: List<LevelAlert>, val state: Map<String, LevelAlertState>)

/**
 * Decides after each reading which level notifications to post (spec 0011). Entering a level with
 * its bell on posts once; the level is then disarmed until the value moves past one of its bounds
 * by the value's margin, so a value that jitters at an edge posts once.
 */
object LevelAlerts {
  private val C_RATE_MARGIN = BigDecimal("0.05")
  private val VOLTAGE_MARGIN_PER_CELL = BigDecimal("0.025")

  /**
   * Checks every value of [reading] against its levels and [state]. A value without a number keeps
   * its state as it is, also when it has none. The first check of a value, or one whose stored
   * level is gone from the list, only stores the level. With [enabled] off the levels are still
   * tracked, so turning it on does not post the level the value is already in.
   */
  fun evaluate(
    reading: BatteryReading,
    settings: Settings,
    enabled: Boolean,
    state: Map<String, LevelAlertState>,
  ): AlertOutcome {
    val basis = LevelBasis.of(reading, settings)
    val alerts = mutableListOf<LevelAlert>()
    val next = state.toMutableMap()
    for (value in LevelValue.entries) {
      val custom = settings.customLevels(value)
      val own = LevelPicker.ownLevels(value, custom)
      val levels = LevelPicker.effective(value, own, basis)
      val shown = ShownNumber.of(value, reading, levels) ?: continue
      val margin = margin(value, basis, own, custom.margin)
      val old = state[value.name] ?: LevelAlertState()
      // The ids of levels the owner has deleted drop out with the re-armed ones.
      val disarmed =
        old.disarmed.filter { id ->
          val level = levels.firstOrNull { it.id == id }
          level != null && !rearms(levels, level, shown, value, margin)
        }
      val pick = LevelPicker.pick(levels, shown)
      val current = old.current
      val entered = current != null && current != pick.id && levels.any { it.id == current }
      val posts = entered && enabled && pick.notify && pick.id !in disarmed
      if (posts) {
        val span = LevelSpans.of(levels, pick, value.decimals, value.floor)
        alerts += LevelAlert(value, pick, span, shown, reading.updatedAtMillis)
      }
      next[value.name] = LevelAlertState(pick.id, if (posts) disarmed + pick.id else disarmed)
    }
    return AlertOutcome(alerts, next)
  }

  /**
   * How far past a level's bounds [value] must move before the level can alert again: the owner's
   * margin in [custom] when [custom] holds valid own levels, else the default. The defaults come
   * from [basis]: the current margin is 0.05 C of the capacity and the voltage margin 0.025 V per
   * cell, both rounded half up to the shown decimals; 1 A without a capacity and 0.4 V without a
   * cell count.
   */
  fun margin(
    value: LevelValue,
    basis: LevelBasis,
    custom: CustomLevels = CustomLevels(),
  ): Double = margin(value, basis, LevelPicker.ownLevels(value, custom), custom.margin)

  /** [margin] for a caller that already has the result of `LevelPicker.ownLevels` as [own]. */
  private fun margin(
    value: LevelValue,
    basis: LevelBasis,
    own: List<ColorLevel>?,
    ownMargin: Double?,
  ): Double = ownMargin?.takeIf { own != null } ?: defaultMargin(value, basis)

  private fun defaultMargin(value: LevelValue, basis: LevelBasis): Double =
    when (value) {
      LevelValue.SOC -> 2.0
      LevelValue.VOLTAGE ->
        basis.cells?.let {
          VOLTAGE_MARGIN_PER_CELL.multiply(BigDecimal(it))
            .setScale(value.decimals, RoundingMode.HALF_UP)
            .toDouble()
        } ?: 0.4
      LevelValue.CHARGE_CURRENT,
      LevelValue.DISCHARGE_CURRENT ->
        basis.capacityAh
          ?.takeIf { it > 0.0 }
          ?.let {
            BigDecimal(it.toString())
              .multiply(C_RATE_MARGIN)
              .setScale(value.decimals, RoundingMode.HALF_UP)
              .toDouble()
          } ?: 1.0
      LevelValue.DELTA -> 3.0
      LevelValue.BATTERY_TEMPERATURE,
      LevelValue.MOSFET_TEMPERATURE -> 2.0
    }

  /**
   * Whether [shown] lies at least [margin] past one of [level]'s bounds. In whole steps of the
   * shown decimals: in doubles 51.2 + 0.1 is above 51.3, so 51.3 V would not re-arm.
   */
  private fun rearms(
    levels: List<ColorLevel>,
    level: ColorLevel,
    shown: Double,
    value: LevelValue,
    margin: Double,
  ): Boolean {
    val (lower, upper) = LevelSpans.bounds(levels, level)
    val x = shown.toSteps(value.decimals)
    val m = margin.toSteps(value.decimals)
    return (upper != null && x >= upper.toSteps(value.decimals) + m) ||
      (lower != null && x <= lower.toSteps(value.decimals) - m)
  }
}
