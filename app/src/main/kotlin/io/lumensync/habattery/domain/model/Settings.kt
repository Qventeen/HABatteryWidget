package io.lumensync.habattery.domain.model

import kotlinx.serialization.Serializable

/**
 * Stored settings. Defaults are empty values (0001 §14), except the display choices and font
 * percents and home-screen scale: they are not user data, so they default to the design values
 * (specs 0003, 0005, 0007).
 */
@Serializable
data class Settings(
  val connection: ConnectionSettings = ConnectionSettings(),
  val entities: EntitySettings = EntitySettings(),
  val refresh: RefreshSettings = RefreshSettings(),
  val notifyOnFull: Boolean = false,
  val capacityAh: Double? = null,
  val display: DisplaySettings = DisplaySettings(),
  val fontPercents: FontPercents = FontPercents(),
  val homeScalePercent: Int = HomeScale.DEFAULT,
  val appearance: AppearanceSettings = AppearanceSettings(),
)

/** Both stored ranges applied (fonts and home-screen scale); readers get only valid values. */
fun Settings.clamped(): Settings =
  copy(fontPercents = fontPercents.clamped(), homeScalePercent = HomeScale.clamp(homeScalePercent))

/** What the Widget section's text-size summary reports: any font off 50 % or the scale off 100 %. */
val Settings.textSizeChanged: Boolean
  get() =
    FontGroup.entries.any { fontPercents[it] != FontPercents.DEFAULT } ||
      homeScalePercent != HomeScale.DEFAULT

/**
 * The home-screen scale in percent (spec 0007, §8): some launchers draw widgets smaller than their
 * reported size (HyperOS about 78 %), so the layout is made 100 / that much bigger. 100 = as
 * reported. The app preview never uses it. Stored values outside 80–150 are clamped when read.
 */
object HomeScale {
  const val MIN = 80
  const val DEFAULT = 100
  const val MAX = 150

  fun clamp(percent: Int): Int = percent.coerceIn(MIN, MAX)
}

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

/** What the widget shows under the SOC number. */
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

/** App-wide display choices for the widget. */
@Serializable
data class DisplaySettings(
  val underSoc: UnderSoc = UnderSoc.KWH,
  val mainValue: MainValue = MainValue.CURRENT,
)

/** The font-size groups of the widget (README "Font sizes"). */
enum class FontGroup {
  SOC,
  UNDER_SOC,
  MAIN,
  CELL,
  SMALL,
}

/**
 * Font sizes as percents (spec 0005, 5.4): 50 % is each layout's design size, 100 % twice it, 25 %
 * half. One value per group for all layouts. Stored values outside 25–100 are clamped when read.
 */
@Serializable
data class FontPercents(
  val soc: Int = DEFAULT,
  val underSoc: Int = DEFAULT,
  val main: Int = DEFAULT,
  val cell: Int = DEFAULT,
  val small: Int = DEFAULT,
) {
  operator fun get(group: FontGroup): Int =
    when (group) {
      FontGroup.SOC -> soc
      FontGroup.UNDER_SOC -> underSoc
      FontGroup.MAIN -> main
      FontGroup.CELL -> cell
      FontGroup.SMALL -> small
    }

  fun with(group: FontGroup, percent: Int): FontPercents =
    when (group) {
      FontGroup.SOC -> copy(soc = percent)
      FontGroup.UNDER_SOC -> copy(underSoc = percent)
      FontGroup.MAIN -> copy(main = percent)
      FontGroup.CELL -> copy(cell = percent)
      FontGroup.SMALL -> copy(small = percent)
    }

  fun clamped(): FontPercents =
    FontGroup.entries.fold(this) { p, group -> p.with(group, p[group].coerceIn(MIN, MAX)) }

  /**
   * The size a text asks for, in dp: the layout's design size × percent / 50 × the system font
   * scale. The drawing may show it smaller when the text does not fit its box (spec 0005, C1).
   */
  fun requestedDp(group: FontGroup, designSp: Float, fontScale: Float): Float =
    designSp * this[group] / DEFAULT * fontScale

  companion object {
    const val MIN = 25
    const val DEFAULT = 50
    const val MAX = 100
    const val STEP = 5
  }
}

/** The colour theme of the app and the widget. */
@Serializable
enum class ThemeChoice {
  SYSTEM,
  LIGHT,
  DARK;

  /** Whether to draw dark, given the system's mode ([systemDark]). */
  fun isDark(systemDark: Boolean): Boolean =
    when (this) {
      SYSTEM -> systemDark
      LIGHT -> false
      DARK -> true
    }
}

/** App-wide look choices (spec 0008). The language is not stored here: the system keeps it. */
@Serializable data class AppearanceSettings(val theme: ThemeChoice = ThemeChoice.SYSTEM)
