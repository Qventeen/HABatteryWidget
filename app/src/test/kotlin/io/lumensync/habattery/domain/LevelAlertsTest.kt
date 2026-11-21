package io.lumensync.habattery.domain

import io.lumensync.habattery.domain.model.BatteryReading
import io.lumensync.habattery.domain.model.CustomLevels
import io.lumensync.habattery.domain.model.InverterReading
import io.lumensync.habattery.domain.model.LevelAlertState
import io.lumensync.habattery.domain.model.LevelName
import io.lumensync.habattery.domain.model.LevelSide
import io.lumensync.habattery.domain.model.LevelValue
import io.lumensync.habattery.domain.model.Settings
import io.lumensync.habattery.domain.model.withCustomLevels
import io.lumensync.habattery.testing.configuredSettings
import io.lumensync.habattery.testing.reading
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LevelAlertsTest {
  private val base = configuredSettings()
  private val noBasis = LevelBasis(null, null)
  private val soc = DefaultLevels.of(LevelValue.SOC, noBasis)
  private val temperature = DefaultLevels.of(LevelValue.BATTERY_TEMPERATURE, noBasis)
  private val twoSensors =
    base.copy(entities = base.entities.copy(temperatures = listOf("sensor.t1", "sensor.t2")))

  private fun socId(name: LevelName) = soc.single { it.defaultName == name }.id

  private fun temperatureId(name: LevelName) = temperature.single { it.defaultName == name }.id

  private fun socState(current: LevelName?, vararg disarmed: LevelName) =
    mapOf("SOC" to LevelAlertState(current?.let(::socId), disarmed.map(::socId)))

  private val grid = DefaultLevels.of(LevelValue.GRID_VOLTAGE, noBasis)

  private fun gridId(name: LevelName) = grid.single { it.defaultName == name }.id

  private fun gridState(current: LevelName, vararg disarmed: LevelName) =
    mapOf("GRID_VOLTAGE" to LevelAlertState(gridId(current), disarmed.map(::gridId)))

  private fun evaluateGrid(volts: Double?, state: Map<String, LevelAlertState>): AlertOutcome =
    LevelAlerts.evaluateInverter(
      InverterReading(gridVoltageV = volts, updatedAtMillis = 1_000L),
      base,
      enabled = true,
      state,
    )

  private fun evaluate(
    reading: BatteryReading,
    state: Map<String, LevelAlertState> = emptyMap(),
    settings: Settings = base,
    enabled: Boolean = true,
  ): AlertOutcome = LevelAlerts.evaluate(reading, settings, enabled, state)

  /** Runs the charges in order from [state]; returns every alert and the last state. */
  private fun run(
    vararg charges: Double,
    state: Map<String, LevelAlertState> = emptyMap(),
    settings: Settings = base,
  ): Pair<List<LevelAlert>, Map<String, LevelAlertState>> {
    var last = state
    val alerts = mutableListOf<LevelAlert>()
    for (charge in charges) {
      val outcome = evaluate(reading(charge = charge), last, settings)
      alerts += outcome.alerts
      last = outcome.state
    }
    return alerts to last
  }

  @Test
  fun firstCheck_storesOnly() {
    val outcome = evaluate(reading(charge = 19.0))
    assertEquals(emptyList<LevelAlert>(), outcome.alerts)
    assertEquals(LevelAlertState(socId(LevelName.LOW)), outcome.state["SOC"])
  }

  @Test
  fun enteringLow_posts() {
    val outcome = evaluate(reading(charge = 19.0), socState(LevelName.GETTING_LOW))
    val alert = outcome.alerts.single()
    assertEquals(LevelValue.SOC, alert.value)
    assertEquals(LevelName.LOW, alert.level.defaultName)
    assertEquals(LevelSide.BELOW, alert.level.side)
    assertEquals(LevelSpan.AtOrBelow(20.0), alert.span)
    assertEquals(19.0, alert.shown, 0.0)
    assertEquals(1_000L, alert.measuredAtMillis)
    assertEquals(
      LevelAlertState(socId(LevelName.LOW), listOf(socId(LevelName.LOW))),
      outcome.state["SOC"],
    )
  }

  @Test
  fun jitter_postsOnce() {
    val (alerts, _) = run(21.0, 19.0, 20.0, 19.0, 20.0)
    assertEquals(listOf(LevelName.LOW to 19.0), alerts.map { it.level.defaultName to it.shown })
  }

  @Test
  fun rearm_atMargin() {
    val (fired, afterLow) = run(19.0, state = socState(LevelName.GETTING_LOW))
    assertEquals(1, fired.size)
    val at21 = evaluate(reading(charge = 21.0), afterLow)
    assertEquals(listOf(socId(LevelName.LOW)), at21.state.getValue("SOC").disarmed)
    val at22 = evaluate(reading(charge = 22.0), at21.state)
    assertEquals(emptyList<Int>(), at22.state.getValue("SOC").disarmed)
    val again = evaluate(reading(charge = 19.0), at22.state)
    assertEquals(LevelName.LOW, again.alerts.single().level.defaultName)
  }

  @Test
  fun full_rising_evenWhileDischarging() {
    val outcome = evaluate(reading(charge = 100.0, current = -5.0), socState(LevelName.NORMAL))
    val alert = outcome.alerts.single()
    assertEquals(LevelName.FULL, alert.level.defaultName)
    assertEquals(LevelSide.ABOVE, alert.level.side)
    assertEquals(LevelSpan.AtOrAbove(100.0), alert.span)
  }

  @Test
  fun disabled_tracksOnly() {
    val off = evaluate(reading(charge = 19.0), socState(LevelName.GETTING_LOW), enabled = false)
    assertEquals(emptyList<LevelAlert>(), off.alerts)
    assertEquals(LevelAlertState(socId(LevelName.LOW)), off.state["SOC"])
    val on = evaluate(reading(charge = 19.0), off.state, enabled = true)
    assertEquals(emptyList<LevelAlert>(), on.alerts)
  }

  @Test
  fun bellOff_noAlert() {
    val outcome = evaluate(reading(charge = 39.0), socState(LevelName.NORMAL))
    assertEquals(emptyList<LevelAlert>(), outcome.alerts)
    assertEquals(LevelAlertState(socId(LevelName.GETTING_LOW)), outcome.state["SOC"])
  }

  @Test
  fun current_otherDirectionCountsZero() {
    val basis = LevelBasis(100.0, null)
    val levels =
      DefaultLevels.copyForCustom(LevelValue.DISCHARGE_CURRENT, basis)
        .map { it.copy(notify = true) }
    val settings =
      base
        .copy(capacityAh = 100.0)
        .withCustomLevels(LevelValue.DISCHARGE_CURRENT, CustomLevels(on = true, levels = levels))
    val light = levels.single { it.defaultName == LevelName.LIGHT }.id
    val outcome =
      evaluate(
        reading(current = -85.0),
        mapOf("DISCHARGE_CURRENT" to LevelAlertState(light)),
        settings,
      )
    val alert = outcome.alerts.single()
    assertEquals(LevelValue.DISCHARGE_CURRENT, alert.value)
    assertEquals(LevelName.VERY_HIGH, alert.level.defaultName)
    assertEquals(85.0, alert.shown, 0.0)
    assertEquals(
      DefaultLevels.of(LevelValue.CHARGE_CURRENT, basis).first().id,
      outcome.state["CHARGE_CURRENT"]?.current,
    )
  }

  @Test
  fun current_unknown_keepsState() {
    val stored =
      mapOf(
        "CHARGE_CURRENT" to LevelAlertState(3, listOf(3)),
        "DISCHARGE_CURRENT" to LevelAlertState(1),
      )
    val outcome = evaluate(reading(current = null), stored, base.copy(capacityAh = 100.0))
    assertEquals(stored["CHARGE_CURRENT"], outcome.state["CHARGE_CURRENT"])
    assertEquals(stored["DISCHARGE_CURRENT"], outcome.state["DISCHARGE_CURRENT"])
  }

  @Test
  fun margin_values() {
    assertEquals(2.0, LevelAlerts.margin(LevelValue.SOC, noBasis), 0.0)
    assertEquals(5.0, LevelAlerts.margin(LevelValue.CHARGE_CURRENT, LevelBasis(100.0, null)), 0.0)
    assertEquals(1.0, LevelAlerts.margin(LevelValue.DISCHARGE_CURRENT, noBasis), 0.0)
    assertEquals(3.0, LevelAlerts.margin(LevelValue.DELTA, noBasis), 0.0)
    assertEquals(2.0, LevelAlerts.margin(LevelValue.BATTERY_TEMPERATURE, noBasis), 0.0)
    assertEquals(2.0, LevelAlerts.margin(LevelValue.MOSFET_TEMPERATURE, noBasis), 0.0)
    assertEquals(5.0, LevelAlerts.margin(LevelValue.GRID_VOLTAGE, noBasis), 0.0)
    assertEquals(5.0, LevelAlerts.margin(LevelValue.OUTPUT_VOLTAGE, noBasis), 0.0)
  }

  @Test
  fun margin_voltage_scalesWithTheCells() {
    fun voltage(cells: Int?) = LevelAlerts.margin(LevelValue.VOLTAGE, LevelBasis(null, cells))
    assertEquals(0.4, voltage(16), 0.0)
    assertEquals(0.38, voltage(15), 0.0) // 0.375
    assertEquals(0.2, voltage(8), 0.0)
    assertEquals(0.1, voltage(4), 0.0)
    assertEquals(0.4, voltage(null), 0.0)
  }

  @Test
  fun margin_current_roundsHalfUpToTheShownDecimals() {
    val charge = LevelAlerts.margin(LevelValue.CHARGE_CURRENT, LevelBasis(105.0, null))
    val discharge = LevelAlerts.margin(LevelValue.DISCHARGE_CURRENT, LevelBasis(314.0, null))
    assertEquals(5.3, charge, 0.0) // 5.25
    assertEquals(15.7, discharge, 0.0)
  }

  @Test
  fun margin_custom() {
    val own = DefaultLevels.copyForCustom(LevelValue.SOC, noBasis)
    fun margin(custom: CustomLevels) = LevelAlerts.margin(LevelValue.SOC, noBasis, custom)
    assertEquals(5.0, margin(CustomLevels(on = true, levels = own, margin = 5.0)), 0.0)
    assertEquals(2.0, margin(CustomLevels(on = false, levels = own, margin = 5.0)), 0.0)
    assertEquals(2.0, margin(CustomLevels(on = true, levels = own, margin = null)), 0.0)
    assertEquals(2.0, margin(CustomLevels(on = true, margin = 5.0)), 0.0)
  }

  @Test
  fun rearm_customMargin() {
    val own = DefaultLevels.copyForCustom(LevelValue.SOC, noBasis)
    val settings =
      base.withCustomLevels(LevelValue.SOC, CustomLevels(on = true, levels = own, margin = 5.0))
    val (fired, afterLow) = run(19.0, state = socState(LevelName.GETTING_LOW), settings = settings)
    assertEquals(1, fired.size)
    val at22 = evaluate(reading(charge = 22.0), afterLow, settings)
    assertEquals(listOf(socId(LevelName.LOW)), at22.state.getValue("SOC").disarmed)
    val at25 = evaluate(reading(charge = 25.0), at22.state, settings)
    assertEquals(emptyList<Int>(), at25.state.getValue("SOC").disarmed)
  }

  @Test
  fun rearm_innerLevel_onBothBounds() {
    val gettingLow = socId(LevelName.GETTING_LOW)
    fun disarmedAt(charge: Double, current: LevelName) =
      evaluate(reading(charge = charge), socState(current, LevelName.GETTING_LOW))
        .state
        .getValue("SOC")
        .disarmed
    assertTrue(gettingLow in disarmedAt(41.0, LevelName.GETTING_LOW))
    assertTrue(gettingLow !in disarmedAt(42.0, LevelName.GETTING_LOW))
    assertTrue(gettingLow in disarmedAt(19.0, LevelName.LOW))
    assertTrue(gettingLow !in disarmedAt(18.0, LevelName.LOW))
  }

  @Test
  fun rearm_middleLevel_onBothBounds() {
    val normal = socId(LevelName.NORMAL)
    fun disarmedAt(charge: Double) =
      evaluate(reading(charge = charge), socState(LevelName.NORMAL, LevelName.NORMAL))
        .state
        .getValue("SOC")
        .disarmed
    assertTrue(normal in disarmedAt(39.0))
    assertTrue(normal !in disarmedAt(38.0))
    assertTrue(normal in disarmedAt(101.0))
    assertTrue(normal !in disarmedAt(102.0))
  }

  @Test
  fun rearm_hotBattery_belowItsEdge() {
    val hot = temperatureId(LevelName.HOT)
    val stored = mapOf("BATTERY_TEMPERATURE" to LevelAlertState(hot, listOf(hot)))
    fun disarmedAt(celsius: Double) =
      evaluate(reading().copy(temperaturesC = listOf(celsius)), stored, twoSensors)
        .state
        .getValue("BATTERY_TEMPERATURE")
        .disarmed
    assertEquals(listOf(hot), disarmedAt(54.0))
    assertEquals(emptyList<Int>(), disarmedAt(53.0))
  }

  @Test
  fun rearm_voltage_comparesWholeSteps() {
    // 16 cells: Getting low ↓51.2 V, High ↑56.8 V and a margin of 0.4 V. The edge plus or minus the
    // margin must re-arm exactly, with no double rounding error in the way.
    val voltage = DefaultLevels.of(LevelValue.VOLTAGE, LevelBasis(null, 16))
    fun id(name: LevelName) = voltage.single { it.defaultName == name }.id
    fun disarmedAt(total: Double, current: LevelName, disarmed: LevelName) =
      evaluate(
          reading().copy(totalVoltageV = total, averageCellVoltageV = total / 16),
          mapOf("VOLTAGE" to LevelAlertState(id(current), listOf(id(disarmed)))),
        )
        .state
        .getValue("VOLTAGE")
        .disarmed
    assertEquals(emptyList<Int>(), disarmedAt(51.6, LevelName.NORMAL, LevelName.GETTING_LOW))
    assertEquals(emptyList<Int>(), disarmedAt(56.4, LevelName.FULL, LevelName.HIGH))
  }

  @Test
  fun batteryTemperature_worstSensor() {
    val stored = mapOf("BATTERY_TEMPERATURE" to LevelAlertState(temperatureId(LevelName.WARM)))
    val hot = evaluate(reading().copy(temperaturesC = listOf(25.0, 56.0)), stored, twoSensors)
    val alert = hot.alerts.single()
    assertEquals(LevelName.HOT, alert.level.defaultName)
    assertEquals(56.0, alert.shown, 0.0)
    val tie = evaluate(reading().copy(temperaturesC = listOf(56.0, 60.0)), stored, twoSensors)
    assertEquals(56.0, tie.alerts.single().shown, 0.0)
  }

  @Test
  fun editedList_storesOnly() {
    val outcome =
      evaluate(reading(charge = 19.0), mapOf("SOC" to LevelAlertState(9, listOf(9))))
    assertEquals(emptyList<LevelAlert>(), outcome.alerts)
    assertEquals(LevelAlertState(socId(LevelName.LOW)), outcome.state["SOC"])
  }

  @Test
  fun editedList_dropsStaleDisarmedIds() {
    val low = socId(LevelName.LOW)
    val outcome =
      evaluate(reading(charge = 19.0), mapOf("SOC" to LevelAlertState(low, listOf(9, low))))
    assertEquals(emptyList<LevelAlert>(), outcome.alerts)
    assertEquals(LevelAlertState(low, listOf(low)), outcome.state["SOC"])
  }

  @Test
  fun oneLevel_neverPosts() {
    // No capacity: the charge current has one level; its bell is on so only the one level stops it.
    val levels =
      DefaultLevels.copyForCustom(LevelValue.CHARGE_CURRENT, noBasis).map { it.copy(notify = true) }
    val settings =
      base.withCustomLevels(LevelValue.CHARGE_CURRENT, CustomLevels(on = true, levels = levels))
    val only = levels.single().id
    val stored = mapOf("CHARGE_CURRENT" to LevelAlertState(only))
    for (current in listOf(150.0, 0.0, -150.0)) {
      val outcome = evaluate(reading(current = current), stored, settings)
      assertEquals(emptyList<LevelAlert>(), outcome.alerts)
      assertEquals(LevelAlertState(only), outcome.state["CHARGE_CURRENT"])
    }
  }

  @Test
  fun missingDelta_keepsState() {
    val stored = mapOf("DELTA" to LevelAlertState(2, listOf(3)))
    val outcome = evaluate(reading().copy(deltaCellVoltageV = null), stored)
    assertEquals(stored["DELTA"], outcome.state["DELTA"])
  }

  @Test
  fun grid_undervoltage_posts() {
    val outcome = evaluateGrid(205.0, gridState(LevelName.NORMAL))
    val alert = outcome.alerts.single()
    assertEquals(LevelValue.GRID_VOLTAGE, alert.value)
    assertEquals(LevelName.LOW, alert.level.defaultName)
    assertEquals(LevelSide.BELOW, alert.level.side)
    assertEquals(LevelSpan.AtOrBelow(206.0), alert.span)
    assertEquals(205.0, alert.shown, 0.0)
    assertEquals(1_000L, alert.measuredAtMillis)
  }

  @Test
  fun grid_roundsToWholeVolts() {
    fun levelAt(volts: Double) =
      evaluateGrid(volts, gridState(LevelName.HIGH)).state.getValue("GRID_VOLTAGE").current
    assertEquals(gridId(LevelName.NORMAL), levelAt(206.6))
    assertEquals(gridId(LevelName.LOW), levelAt(206.4))
    // Half-even, as the strip shows it: 206.5 V is "206 V", so Low.
    assertEquals(gridId(LevelName.LOW), levelAt(206.5))
  }

  @Test
  fun grid_rearm5V() {
    val fired = evaluateGrid(205.0, gridState(LevelName.NORMAL))
    assertEquals(1, fired.alerts.size)
    fun disarmedAt(volts: Double) =
      evaluateGrid(volts, fired.state).state.getValue("GRID_VOLTAGE").disarmed
    val low = gridId(LevelName.LOW)
    assertEquals(listOf(low), disarmedAt(210.0))
    assertEquals(emptyList<Int>(), disarmedAt(211.0))
    assertEquals(listOf(low), disarmedAt(95.0))
    assertEquals(emptyList<Int>(), disarmedAt(94.0))
  }

  @Test
  fun inverter_nullVoltage_keepsState() {
    val stored =
      gridState(LevelName.LOW, LevelName.LOW) +
        ("OUTPUT_VOLTAGE" to LevelAlertState(3)) +
        socState(LevelName.NORMAL)
    val outcome =
      LevelAlerts.evaluateInverter(InverterReading(updatedAtMillis = 1_000L), base, true, stored)
    assertEquals(emptyList<LevelAlert>(), outcome.alerts)
    assertEquals(stored, outcome.state)
  }
}
