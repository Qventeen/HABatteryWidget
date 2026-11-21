package io.lumensync.habattery.domain.model

import kotlinx.serialization.Serializable

/** The three inverter sensors, filled from the [prefix] like the battery's entities. */
@Serializable
data class InverterEntitySettings(
  val prefix: String = "",
  val mode: String = "",
  val gridVoltage: String = "",
  val outputVoltage: String = "",
)

/** How the strip shows the operation mode. */
@Serializable
enum class ModeStyle {
  BIG_ICON,
  ICON_WORD,
}

/** The icons the user can pick for the strip. Names are stored, so the order may change. */
enum class InverterIcon {
  TOWER,
  LOCATION_CITY,
  ELECTRICAL_SERVICES,
  POWER_INPUT,
  ELECTRIC_METER,
  FACTORY,
  APARTMENT,
  OFFLINE_BOLT,
  BOLT,
  ELECTRIC_BOLT,
  BATTERY_CHARGING_FULL,
  BATTERY_FULL,
  BATTERY_5_BAR,
  BATTERY_ANDROID_FULL,
  BATTERY_HORIZ_075,
  ENERGY_SAVINGS_LEAF,
  NEST_ECO_LEAF,
  ENERGY_PROGRAM_SAVING,
  SOLAR_POWER,
  HELP,
  QUESTION_MARK,
  DEVICE_UNKNOWN,
  PENDING,
  INFO,
  POWER,
  HOME,
  HOUSE,
  COTTAGE,
  HOME_WORK,
  HOME_LIGHTNING_BOLT,
  OUTLET,
  POWER_SETTINGS_NEW,
  RESTART_ALT,
  MODE_STANDBY,
  BEDTIME,
  ALT_ROUTE,
  CALL_SPLIT,
  SWAP_HORIZ,
  CHARGER,
  BATTERY_CHARGING_90,
  EV_CHARGER,
  ERROR,
  REPORT,
  WARNING,
  DANGEROUS,
}

/**
 * A place on the strip with its own icon choice. Qualified names: a bare one is the slot itself.
 * The mode icons are stored with the modes (`TextLevel.icon`).
 */
enum class IconSlot(val default: InverterIcon) {
  GRID_VOLTAGE(InverterIcon.TOWER),
  OUTPUT_VOLTAGE(InverterIcon.HOME),
}

/** Font percents of the strip's mode word and voltage numbers; same range and step as [FontPercents]. */
@Serializable
data class InverterFontPercents(
  val word: Int = FontPercents.DEFAULT,
  val value: Int = FontPercents.DEFAULT,
) {
  fun clamped(): InverterFontPercents =
    copy(
      word = word.coerceIn(FontPercents.MIN, FontPercents.MAX),
      value = value.coerceIn(FontPercents.MIN, FontPercents.MAX),
    )
}

/** Stored inverter settings. Display choices default to the design values, like [Settings]. */
@Serializable
data class InverterSettings(
  val entities: InverterEntitySettings = InverterEntitySettings(),
  val modeStyle: ModeStyle = ModeStyle.BIG_ICON,
  /** [IconSlot] name to [InverterIcon] name: a name written by a later version cannot break the file. */
  val icons: Map<String, String> = emptyMap(),
  val fontPercents: InverterFontPercents = InverterFontPercents(),
)

/** The chosen icon of [slot]; a missing or unknown name gives the slot's default. */
fun InverterSettings.icon(slot: IconSlot): InverterIcon =
  icons[slot.name]?.let { name -> InverterIcon.entries.firstOrNull { it.name == name } } ?: slot.default

fun InverterSettings.withIcon(slot: IconSlot, icon: InverterIcon): InverterSettings =
  copy(icons = icons + (slot.name to icon.name))
