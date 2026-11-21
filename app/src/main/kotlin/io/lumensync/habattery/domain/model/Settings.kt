package io.lumensync.habattery.domain.model

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

/**
 * Stored settings. Defaults are empty values (0001 §14), except the display choices, the font
 * percents, the home-screen scale and the [appearance] (theme): they are not user data, so they
 * default to the design values (specs 0003, 0005, 0007, 0008).
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class Settings(
  val connection: ConnectionSettings = ConnectionSettings(),
  val entities: EntitySettings = EntitySettings(),
  val refresh: RefreshSettings = RefreshSettings(),
  /** The main notification switch; a v1 file stored it as `notifyOnFull`. */
  @JsonNames("notifyOnFull") val notificationsOn: Boolean = false,
  val capacityAh: Double? = null,
  val display: DisplaySettings = DisplaySettings(),
  val fontPercents: FontPercents = FontPercents(),
  val homeScalePercent: Int = HomeScale.DEFAULT,
  val appearance: AppearanceSettings = AppearanceSettings(),
  /** Keyed by [LevelValue.name]: a key written by a later version cannot break the file. */
  val colorLevels: Map<String, CustomLevels> = emptyMap(),
  val inverter: InverterSettings = InverterSettings(),
)

/** Both stored ranges applied (fonts and home-screen scale); readers get only valid values. */
fun Settings.clamped(): Settings =
  copy(
    fontPercents = fontPercents.clamped(),
    homeScalePercent = HomeScale.clamp(homeScalePercent),
    inverter = inverter.copy(fontPercents = inverter.fontPercents.clamped()),
  )

/**
 * What the battery Widget section's text-size summary reports: any battery font off 50 %. The
 * home-screen scale has its own section.
 */
val Settings.textSizeChanged: Boolean
  get() = FontGroup.entries.any { fontPercents[it] != FontPercents.DEFAULT }

/**
 * The home-screen scale in percent (spec 0007, §8): some launchers draw widgets smaller than their
 * reported size (HyperOS about 78 %), so the layout is made 100 / that much bigger. 100 = as
 * reported. The app preview never uses it. Stored values outside 80–200 are clamped when read.
 */
object HomeScale {
  const val MIN = 80
  const val DEFAULT = 100
  const val MAX = 200

  fun clamp(percent: Int): Int = percent.coerceIn(MIN, MAX)

  /** The multiplier the widget layout uses: 128 % is 1.28. */
  fun factor(percent: Int): Float = percent / 100f
}

/** Which kind of Home Assistant token the user stored (spec 0009). */
@Serializable
enum class TokenType {
  ADMIN,
  USER,
}

@Serializable
data class ConnectionSettings(
  val url: String = "",
  /**
   * Not user data, so the default is [TokenType.ADMIN], not empty (0001 §14 exception, like
   * [ThemeChoice]): settings stored before this field load as ADMIN.
   */
  val tokenType: TokenType = TokenType.ADMIN,
)

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

/**
 * The font-size groups of the widget (README "Font sizes"). [base] multiplies the design size: the
 * small texts read too small on the phone, so their 50 % is 1.5 × the design (owner, 2026-11-16).
 */
enum class FontGroup(val base: Float = 1f) {
  SOC,
  UNDER_SOC,
  MAIN,
  CELL,
  SMALL(base = 1.5f),
}

/**
 * Font sizes as percents (spec 0005, 5.4): 50 % is each layout's design size (× the group's
 * [FontGroup.base]), 100 % twice it, 10 % a fifth. One value per group for all layouts. Stored
 * values outside 10–100 are clamped when read.
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
   * The size a text asks for, in dp: the layout's design size × the group's [FontGroup.base] ×
   * percent / 50 × the system font scale. The drawing may show it smaller when the text does not
   * fit its box (spec 0005, C1).
   */
  fun requestedDp(group: FontGroup, designSp: Float, fontScale: Float): Float =
    designSp * group.base * this[group] / DEFAULT * fontScale

  companion object {
    const val MIN = 10
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
