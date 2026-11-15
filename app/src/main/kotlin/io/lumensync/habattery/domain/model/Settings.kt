package io.lumensync.habattery.domain.model

import kotlin.math.min
import kotlinx.serialization.Serializable

/**
 * Stored settings. Defaults are empty values (0001 §14), except the display choices and font
 * sizes: they are not user data, so they default to the design values (spec 0003).
 */
@Serializable
data class Settings(
  val connection: ConnectionSettings = ConnectionSettings(),
  val entities: EntitySettings = EntitySettings(),
  val refresh: RefreshSettings = RefreshSettings(),
  val notifyOnFull: Boolean = false,
  val capacityAh: Double? = null,
  val display: DisplaySettings = DisplaySettings(),
  val fontSizes: FontSizes = FontSizes(),
)

@Serializable
data class ConnectionSettings(val url: String = "")

@Serializable
data class EntitySettings(
  val prefix: String = "",
  val charge: String = "",
  val totalVoltage: String = "",
  val current: String = "",
  val power: String = "",
  val capacityRemaining: String = "",
  val totalCapacity: String = "",
  val averageCellVoltage: String = "",
  val deltaCellVoltage: String = "",
  val mosfetTemperature: String = "",
  val temperatures: List<String> = emptyList(),
  val balancing: String = "",
) {
  companion object {
    const val MAX_TEMPERATURES = 8
  }
}

@Serializable
data class RefreshSettings(val auto: Boolean = false, val intervalMinutes: Int? = null) {
  /**
   * The widget's auto toggle. Turning on keeps a valid interval, brings one out of range into range,
   * and uses [DEFAULT_INTERVAL] when none is set. Turning off keeps the interval.
   */
  fun toggled(): RefreshSettings =
    if (auto) copy(auto = false)
    else RefreshSettings(true, intervalMinutes?.coerceIn(MIN_INTERVAL, MAX_INTERVAL) ?: DEFAULT_INTERVAL)

  companion object {
    const val MIN_INTERVAL = 15
    const val MAX_INTERVAL = 1440
    const val DEFAULT_INTERVAL = 15
  }
}

/** What the widget and the app card show under the SOC number. */
@Serializable
enum class UnderSoc {
  KWH,
  AH,
}

/** What the second big pill shows. */
@Serializable
enum class MainValue {
  CURRENT,
  POWER,
}

/** App-wide display choices for the widget and the app card. */
@Serializable
data class DisplaySettings(
  val underSoc: UnderSoc = UnderSoc.KWH,
  val mainValue: MainValue = MainValue.CURRENT,
)

/** Font-size groups in sp: default and allowed range (checked on design v4 at every maximum). */
enum class FontGroup(val default: Float, val min: Float, val max: Float, val step: Float) {
  SOC(40f, 32f, 42f, 1f),
  UNDER_SOC(12f, 10f, 12f, 1f),
  MAIN(22f, 18f, 24f, 1f),
  CELL(14f, 12f, 15f, 1f),
  SMALL(10.5f, 9f, 12f, 0.5f),
}

@Serializable
data class FontSizes(
  val soc: Float = FontGroup.SOC.default,
  val underSoc: Float = FontGroup.UNDER_SOC.default,
  val main: Float = FontGroup.MAIN.default,
  val cell: Float = FontGroup.CELL.default,
  val small: Float = FontGroup.SMALL.default,
) {
  operator fun get(group: FontGroup): Float =
    when (group) {
      FontGroup.SOC -> soc
      FontGroup.UNDER_SOC -> underSoc
      FontGroup.MAIN -> main
      FontGroup.CELL -> cell
      FontGroup.SMALL -> small
    }

  fun with(group: FontGroup, sp: Float): FontSizes =
    when (group) {
      FontGroup.SOC -> copy(soc = sp)
      FontGroup.UNDER_SOC -> copy(underSoc = sp)
      FontGroup.MAIN -> copy(main = sp)
      FontGroup.CELL -> copy(cell = sp)
      FontGroup.SMALL -> copy(small = sp)
    }

  /** Every size inside its group's range; a non-finite size becomes the default. */
  fun clamped(): FontSizes =
    FontGroup.entries.fold(this) { sizes, group ->
      val sp = sizes[group]
      sizes.with(group, if (sp.isFinite()) sp.coerceIn(group.min, group.max) else group.default)
    }

  /** The size to draw: the setting times the system font scale, never above the group's maximum. */
  fun effective(group: FontGroup, fontScale: Float): Float = min(this[group] * fontScale, group.max)
}
