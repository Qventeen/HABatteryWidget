package io.lumensync.habattery.domain.model

import kotlinx.serialization.Serializable

/** Most levels one value can have. */
const val MAX_LEVELS = 6

/** How serious a level is. [rank] orders them: Info ranks above Normal. */
@Serializable
enum class LevelType(val rank: Int, val defaultColor: LevelColor) {
  CRITICAL(3, LevelColor.RED),
  WARNING(2, LevelColor.AMBER),
  NORMAL(0, LevelColor.GREEN),
  INFO(1, LevelColor.BLUE),
}

/** The palette: eight colours and PLAIN, the neutral text colour. */
@Serializable
enum class LevelColor {
  PLAIN,
  RED,
  ORANGE,
  AMBER,
  GREEN,
  TEAL,
  BLUE,
  VIOLET,
  GREY,
}

/** Icons for temperature levels; no icon is `null`. */
@Serializable
enum class LevelIcon {
  SNOWFLAKE,
  LEAF,
  FLAME,
  FLAME_ALERT,
}

/** The default level names. They are stored as keys and translated when shown. */
@Serializable
enum class LevelName {
  LOW,
  GETTING_LOW,
  NORMAL,
  FULL,
  FREEZING,
  COOL,
  WARM,
  HOT,
  BALANCED,
  HIGH,
  VERY_HIGH,
  LIGHT,
}

/** The values that have levels. [decimals] is the precision of an edge; only temperatures have icons. */
enum class LevelValue(val decimals: Int, val hasIcons: Boolean) {
  SOC(0, false),
  VOLTAGE(2, false),
  CHARGE_CURRENT(1, false),
  DISCHARGE_CURRENT(1, false),
  DELTA(0, false),
  BATTERY_TEMPERATURE(0, true),
  MOSFET_TEMPERATURE(0, true),
}

/**
 * One band of a value. [from] includes its edge and is `null` only on the first level. [id] stays
 * the same when the level moves or is renamed. A blank [name] means "show [defaultName]".
 */
@Serializable
data class ColorLevel(
  val id: Int = 0,
  val from: Double? = null,
  /** Not user data, so the default is NORMAL, not empty (0001 §14 exception, like the display choices). */
  val type: LevelType = LevelType.NORMAL,
  val color: LevelColor? = null,
  val icon: LevelIcon? = null,
  val name: String = "",
  val defaultName: LevelName? = null,
) {
  /** The chosen colour, or the colour of the [type]. */
  val shownColor: LevelColor
    get() = color ?: type.defaultColor
}

/** The owner's own levels for one value; when [on] is false the built-in levels apply. */
@Serializable
data class CustomLevels(val on: Boolean = false, val levels: List<ColorLevel> = emptyList())

/** What the widget draws for a value. Not stored. */
data class LevelLook(val color: LevelColor, val icon: LevelIcon? = null)

/** The custom levels of [value]; none stored means [CustomLevels] with the switch off. */
fun Settings.customLevels(value: LevelValue): CustomLevels =
  colorLevels[value.name] ?: CustomLevels()

fun Settings.withCustomLevels(value: LevelValue, levels: CustomLevels): Settings =
  copy(colorLevels = colorLevels + (value.name to levels))
