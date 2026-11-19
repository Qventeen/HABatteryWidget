package io.lumensync.habattery.domain

import io.lumensync.habattery.domain.model.BatteryReading
import io.lumensync.habattery.domain.model.ColorLevel
import io.lumensync.habattery.domain.model.LevelColor
import io.lumensync.habattery.domain.model.LevelLook
import io.lumensync.habattery.domain.model.LevelValue
import io.lumensync.habattery.domain.model.MainValue
import io.lumensync.habattery.domain.model.Settings
import io.lumensync.habattery.domain.model.SyncError
import io.lumensync.habattery.domain.model.UnderSoc
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.time.Duration

/**
 * What the widget shows, on the home screen and in the app's preview (spec 0006); the drawing only
 * reads it. A missing value stays null here; the drawing shows "—".
 */
sealed interface WidgetModel {
  enum class EmptyKind {
    SETUP,
    NO_DATA,
  }

  /** Current direction: UP charging, DOWN discharging, NONE idle or unknown. */
  enum class Flow {
    UP,
    DOWN,
    NONE,
  }

  /**
   * The battery temperature pill (spec 0004). [Battery.celsius] is the mean of the sensors that have
   * a value; [Battery.look] comes from the worst level of those sensors, so one hot sensor is not
   * hidden.
   */
  sealed interface Temperatures {
    /** No battery sensor configured: no battery pill. */
    data object None : Temperatures

    /** [sensors] is the number of configured sensors, not of those that have a value. */
    data class Battery(val celsius: Double?, val look: LevelLook, val sensors: Int) : Temperatures
  }

  data class Empty(val kind: EmptyKind, val error: SyncError?) : WidgetModel

  data class Data(
    val socPercent: Int,
    val ringFraction: Float,
    val ringLook: LevelLook,
    val underSoc: UnderSoc,
    val underSocValue: Double?,
    val voltage: Double?,
    val voltageLook: LevelLook,
    val main: MainValue,
    val mainValue: Double?,
    val flow: Flow,
    val mainLook: LevelLook,
    val timeLeft: Duration?,
    val balancing: Boolean?,
    val updatedAtMillis: Long,
    val error: SyncError?,
    val refreshing: Boolean,
    val autoRefresh: Boolean,
    val averageCellVoltage: Double?,
    val averageCellLook: LevelLook,
    val deltaMv: Int?,
    val deltaLook: LevelLook,
    val temperatures: Temperatures,
    val mosfet: Double?,
    val mosfetLook: LevelLook,
  ) : WidgetModel

  companion object {
    fun build(overview: BatteryOverview, settings: Settings): WidgetModel {
      if (overview.setupNeeded) return Empty(EmptyKind.SETUP, null)
      val r = overview.reading ?: return Empty(EmptyKind.NO_DATA, overview.error)
      val soc = r.chargePercent.roundToInt()
      val flow = flow(r.currentA)
      val deltaMv = BatteryMath.deltaMv(r)
      val display = settings.display
      val capacity = BatteryMath.fullCapacityAh(r, settings.capacityAh)
      fun levels(value: LevelValue) = LevelPicker.effective(value, settings, capacity)
      // Each value picks its level by the number as shown, so colour and number agree at an edge.
      fun look(value: LevelValue, shown: Double?): LevelLook =
        shown?.let { LevelPicker.pick(levels(value), it).toLook(value) } ?: PLAIN
      // The pill may show watts; its levels are in amps either way.
      val amps = r.currentA?.let { abs(it).roundTo(1) }
      return Data(
        socPercent = soc,
        ringFraction = BatteryMath.progress(r.chargePercent),
        ringLook = look(LevelValue.SOC, soc.toDouble()),
        underSoc = display.underSoc,
        underSocValue = underSocValue(r, display.underSoc),
        voltage = r.totalVoltageV,
        voltageLook = look(LevelValue.VOLTAGE, r.totalVoltageV?.roundTo(2)),
        main = display.mainValue,
        mainValue =
          when (display.mainValue) {
            MainValue.CURRENT -> r.currentA
            MainValue.POWER -> r.powerW
          }?.let(::abs),
        flow = flow,
        mainLook =
          when (flow) {
            Flow.UP -> look(LevelValue.CHARGE_CURRENT, amps)
            Flow.DOWN -> look(LevelValue.DISCHARGE_CURRENT, amps)
            Flow.NONE -> LevelLook(LevelColor.GREY)
          },
        timeLeft = overview.timeRemaining?.duration,
        balancing = r.balancing,
        updatedAtMillis = r.updatedAtMillis,
        error = overview.error,
        refreshing = overview.refreshing,
        autoRefresh = settings.refresh.auto,
        averageCellVoltage = r.averageCellVoltageV,
        averageCellLook = averageCellLook(r, levels(LevelValue.VOLTAGE)),
        deltaMv = deltaMv,
        deltaLook = look(LevelValue.DELTA, deltaMv?.toDouble()),
        temperatures =
          temperatures(
            r.temperaturesC,
            settings.entities.temperatures.size,
            levels(LevelValue.BATTERY_TEMPERATURE),
          ),
        mosfet = r.mosfetTemperatureC,
        mosfetLook = look(LevelValue.MOSFET_TEMPERATURE, r.mosfetTemperatureC?.let(::shown)),
      )
    }

    private val PLAIN = LevelLook(LevelColor.PLAIN)

    private fun ColorLevel.toLook(value: LevelValue): LevelLook =
      LevelLook(shownColor, icon.takeIf { value.hasIcons })

    /** The voltage levels per cell; plain without a cell count. */
    private fun averageCellLook(r: BatteryReading, voltageLevels: List<ColorLevel>): LevelLook {
      val average = r.averageCellVoltageV ?: return PLAIN
      val cells = BatteryMath.cellCount(r) ?: return PLAIN
      return LevelPicker.pick(LevelPicker.perCell(voltageLevels, cells), average.roundTo(3))
        .toLook(LevelValue.VOLTAGE)
    }

    /** Temperatures show in whole degrees; levels use the same value, so colour and number agree. */
    private fun shown(celsius: Double): Double = celsius.roundToInt().toDouble()

    private fun flow(currentA: Double?): Flow =
      when {
        currentA == null -> Flow.NONE
        currentA > BatteryMath.IDLE_CURRENT_A -> Flow.UP
        currentA < -BatteryMath.IDLE_CURRENT_A -> Flow.DOWN
        else -> Flow.NONE
      }

    private fun underSocValue(r: BatteryReading, kind: UnderSoc): Double? {
      val ahLeft = r.capacityRemainingAh ?: return null
      return when (kind) {
        UnderSoc.AH -> ahLeft
        UnderSoc.KWH -> r.totalVoltageV?.let { ahLeft * it / 1000 }
      }
    }

    private fun temperatures(
      values: List<Double?>,
      configured: Int,
      levels: List<ColorLevel>,
    ): Temperatures {
      if (configured == 0) return Temperatures.None
      val known = values.filterNotNull()
      val worst = LevelPicker.worst(known.map { LevelPicker.pick(levels, shown(it)) })
      return Temperatures.Battery(
        known.takeIf { it.isNotEmpty() }?.average(),
        worst?.toLook(LevelValue.BATTERY_TEMPERATURE) ?: PLAIN,
        configured,
      )
    }
  }
}
