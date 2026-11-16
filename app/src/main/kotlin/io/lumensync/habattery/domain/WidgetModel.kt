package io.lumensync.habattery.domain

import io.lumensync.habattery.domain.model.BatteryReading
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
   * a value; [Battery.level] is the worst level of those sensors, so one hot sensor is not hidden.
   */
  sealed interface Temperatures {
    /** No battery sensor configured: no battery pill. */
    data object None : Temperatures

    /** [sensors] is the number of configured sensors, not of those that have a value. */
    data class Battery(val celsius: Double?, val level: Level, val sensors: Int) : Temperatures
  }

  data class Empty(val kind: EmptyKind, val error: SyncError?) : WidgetModel

  data class Data(
    val socPercent: Int,
    val ringFraction: Float,
    val ringLevel: Level,
    val underSoc: UnderSoc,
    val underSocValue: Double?,
    val voltage: Double?,
    val main: MainValue,
    val mainValue: Double?,
    val flow: Flow,
    val mainLevel: Level,
    val timeLeft: Duration?,
    val balancing: Boolean?,
    val updatedAtMillis: Long,
    val error: SyncError?,
    val refreshing: Boolean,
    val autoRefresh: Boolean,
    val averageCellVoltage: Double?,
    val deltaMv: Int?,
    val deltaLevel: Level,
    val temperatures: Temperatures,
    val mosfet: Double?,
    val mosfetLevel: Level,
  ) : WidgetModel

  companion object {
    fun build(overview: BatteryOverview, settings: Settings): WidgetModel {
      if (overview.setupNeeded) return Empty(EmptyKind.SETUP, null)
      val r = overview.reading ?: return Empty(EmptyKind.NO_DATA, overview.error)
      val soc = r.chargePercent.roundToInt()
      val flow = flow(r.currentA)
      val deltaMv = BatteryMath.deltaMv(r)
      val display = settings.display
      return Data(
        socPercent = soc,
        ringFraction = BatteryMath.progress(r.chargePercent),
        ringLevel = WidgetLevels.charge(soc),
        underSoc = display.underSoc,
        underSocValue = underSocValue(r, display.underSoc),
        voltage = r.totalVoltageV,
        main = display.mainValue,
        mainValue =
          when (display.mainValue) {
            MainValue.CURRENT -> r.currentA
            MainValue.POWER -> r.powerW
          }?.let(::abs),
        flow = flow,
        mainLevel =
          when (flow) {
            Flow.UP -> Level.OK
            Flow.DOWN -> Level.WARN
            Flow.NONE -> Level.IDLE
          },
        timeLeft = overview.timeRemaining?.duration,
        balancing = r.balancing,
        updatedAtMillis = r.updatedAtMillis,
        error = overview.error,
        refreshing = overview.refreshing,
        autoRefresh = settings.refresh.auto,
        averageCellVoltage = r.averageCellVoltageV,
        deltaMv = deltaMv,
        deltaLevel = deltaMv?.let(WidgetLevels::delta) ?: Level.PLAIN,
        temperatures = temperatures(r.temperaturesC, settings.entities.temperatures.size),
        mosfet = r.mosfetTemperatureC,
        mosfetLevel = r.mosfetTemperatureC?.let { WidgetLevels.mosfet(shown(it)) } ?: Level.PLAIN,
      )
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

    private fun temperatures(values: List<Double?>, configured: Int): Temperatures {
      if (configured == 0) return Temperatures.None
      val known = values.filterNotNull()
      val worst =
        known.map { WidgetLevels.battery(shown(it)) }.maxByOrNull(::severity) ?: Level.PLAIN
      return Temperatures.Battery(known.takeIf { it.isNotEmpty() }?.average(), worst, configured)
    }

    private fun severity(level: Level): Int =
      when (level) {
        Level.BAD -> 2
        Level.WARN -> 1
        else -> 0
      }
  }
}
