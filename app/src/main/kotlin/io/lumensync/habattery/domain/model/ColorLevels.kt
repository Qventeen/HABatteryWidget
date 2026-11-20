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

/**
 * Where a level lies against its edge: [BELOW] covers the edge and everything below it, [ABOVE] the
 * edge and everything above it, [MIDDLE] what is left between them and has no edge.
 */
@Serializable
enum class LevelSide {
  BELOW,
  MIDDLE,
  ABOVE,
}

/**
 * The values that have levels. [decimals] is the precision of an edge; only temperatures have
 * icons. [floor] is the lowest number the value can show, or `null` when it can go negative.
 */
enum class LevelValue(val decimals: Int, val hasIcons: Boolean, val floor: Double?) {
  SOC(0, false, 0.0),
  VOLTAGE(2, false, 0.0),
  CHARGE_CURRENT(1, false, 0.0),
  DISCHARGE_CURRENT(1, false, 0.0),
  DELTA(0, false, 0.0),
  BATTERY_TEMPERATURE(0, true, null),
  MOSFET_TEMPERATURE(0, true, null),
}

/**
 * One band of a value. A [LevelSide.BELOW] or [LevelSide.ABOVE] level has an [edge]; the one
 * [LevelSide.MIDDLE] level has none. [id] stays the same when the level moves or is renamed. A
 * blank [name] means "show [defaultName]".
 */
@Serializable
data class ColorLevel(
  val id: Int = 0,
  /** The middle level is the one with no edge, so it is the default. */
  val side: LevelSide = LevelSide.MIDDLE,
  val edge: Double? = null,
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

/** The owner's own levels for one value; they count only through `LevelPicker.ownLevels`. */
@Serializable
data class CustomLevels(val on: Boolean = false, val levels: List<ColorLevel> = emptyList())

/** What the widget draws for a value. Not stored. */
data class LevelLook(val color: LevelColor, val icon: LevelIcon? = null)

/** The custom levels of [value]; none stored means an empty [CustomLevels]. */
fun Settings.customLevels(value: LevelValue): CustomLevels =
  colorLevels[value.name] ?: CustomLevels()

fun Settings.withCustomLevels(value: LevelValue, levels: CustomLevels): Settings =
  copy(colorLevels = colorLevels + (value.name to levels))
