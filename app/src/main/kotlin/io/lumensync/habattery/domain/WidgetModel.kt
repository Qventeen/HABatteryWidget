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
 * What the widget and the app card show (spec 0003, 5.2). Both only draw it. A missing value stays
 * null here; the drawing shows "—".
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

  /** The battery temperature pills, by the number of configured sensors (a stable layout). */
  sealed interface Temperatures {
    data object None : Temperatures

    data class One(val celsius: Double?, val level: Level) : Temperatures

    data class MaxMin(val max: Double?, val maxLevel: Level, val min: Double?, val minLevel: Level) :
      Temperatures
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
        mosfetLevel = r.mosfetTemperatureC?.let(WidgetLevels::mosfet) ?: Level.PLAIN,
      )
    }

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
      fun level(c: Double?) = c?.let(WidgetLevels::battery) ?: Level.PLAIN
      return when (configured) {
        0 -> Temperatures.None
        1 -> values.firstOrNull().let { Temperatures.One(it, level(it)) }
        else -> {
          val known = values.filterNotNull()
          val max = known.maxOrNull()
          val min = known.minOrNull()
          Temperatures.MaxMin(max, level(max), min, level(min))
        }
      }
    }
  }
}
