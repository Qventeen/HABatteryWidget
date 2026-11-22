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
    /** The time the model was built for; the update time is told against it, not the clock. */
    val nowMillis: Long,
    val error: SyncError?,
    val refreshing: Boolean,
    /** The freshness dot turns amber ([Freshness.isLate]). */
    val dataLate: Boolean,
    val dataAgeMinutes: Long,
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
      val soc = ShownNumber.soc(r)
      val flow = flow(r.currentA)
      val deltaMv = BatteryMath.deltaMv(r)
      val display = settings.display
      val basis = LevelBasis.of(r, settings)
      fun levels(value: LevelValue) = LevelPicker.effective(value, settings, basis)
      fun look(value: LevelValue): LevelLook {
        val levels = levels(value)
        return ShownNumber.of(value, r, levels)?.let { LevelPicker.pick(levels, it).toLook(value) }
          ?: PLAIN
      }
      val mainValue =
        when (display.mainValue) {
          MainValue.CURRENT -> r.currentA
          MainValue.POWER -> r.powerW
        }?.let(::abs)
      return Data(
        socPercent = soc,
        ringFraction = BatteryMath.progress(r.chargePercent),
        ringLook = look(LevelValue.SOC),
        underSoc = display.underSoc,
        underSocValue = underSocValue(r, display.underSoc),
        voltage = r.totalVoltageV,
        voltageLook = look(LevelValue.VOLTAGE),
        main = display.mainValue,
        mainValue = mainValue,
        flow = flow,
        mainLook =
          when {
            mainValue == null -> PLAIN
            flow == Flow.NONE -> LevelLook(LevelColor.GREY)
            // The pill may show watts; its levels are in amps either way.
            flow == Flow.UP -> look(LevelValue.CHARGE_CURRENT)
            else -> look(LevelValue.DISCHARGE_CURRENT)
          },
        timeLeft = overview.timeRemaining?.duration,
        balancing = r.balancing,
        updatedAtMillis = r.updatedAtMillis,
        nowMillis = overview.nowMillis,
        error = overview.error,
        refreshing = overview.refreshing,
        dataLate = Freshness.isLate(r.updatedAtMillis, overview.nowMillis, overview.refresh),
        dataAgeMinutes = Freshness.ageMinutes(r.updatedAtMillis, overview.nowMillis),
        averageCellVoltage = r.averageCellVoltageV,
        averageCellLook =
          averageCellLook(r.averageCellVoltageV, basis.cells, levels(LevelValue.VOLTAGE)),
        deltaMv = deltaMv,
        deltaLook = look(LevelValue.DELTA),
        temperatures =
          temperatures(
            r.temperaturesC,
            settings.entities.temperatures.size,
            look(LevelValue.BATTERY_TEMPERATURE),
          ),
        mosfet = r.mosfetTemperatureC,
        mosfetLook = look(LevelValue.MOSFET_TEMPERATURE),
      )
    }

    private val PLAIN = LevelLook(LevelColor.PLAIN)

    private fun ColorLevel.toLook(value: LevelValue): LevelLook =
      LevelLook(shownColor, icon.takeIf { value.hasIcons })

    /** The voltage levels per cell; plain without a cell count. */
    private fun averageCellLook(
      average: Double?,
      cells: Int?,
      voltageLevels: List<ColorLevel>,
    ): LevelLook {
      if (average == null || cells == null) return PLAIN
      val perCell = LevelPicker.perCell(voltageLevels, cells)
      return LevelPicker.pick(perCell, average.roundTo(LevelPicker.CELL_DECIMALS))
        .toLook(LevelValue.VOLTAGE)
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

    private fun temperatures(
      values: List<Double?>,
      configured: Int,
      look: LevelLook,
    ): Temperatures {
      if (configured == 0) return Temperatures.None
      val known = values.filterNotNull()
      return Temperatures.Battery(known.takeIf { it.isNotEmpty() }?.average(), look, configured)
    }
  }
}
