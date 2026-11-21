package io.lumensync.habattery.domain.model

import kotlinx.serialization.Serializable

/** Most text levels one value can have, "other" included. */
const val MAX_TEXT_LEVELS = 12

/**
 * The text values that have text levels: a text finds its level by its texts, as a number finds
 * its level by an edge. Append only: the names are stored keys.
 */
enum class TextValue {
  MODE,
}

/** The default names of text levels. They are stored as keys and translated when shown. */
@Serializable
enum class TextLevelName {
  POWER_ON,
  STANDBY,
  MAINS,
  OFF_GRID,
  BYPASS,
  CHARGING,
  FAULT,
}

/**
 * One item of a text value. A text equal to one of [texts] (trimmed, ignoring case) picks it; the
 * one [other] item has no texts and takes every text no item lists. A blank [name] means "show
 * [defaultName]". [icon] is an [InverterIcon] name, so a name from a later build cannot break the
 * file. With [notify] on, entering the item raises an alert.
 */
@Serializable
data class TextLevel(
  val id: Int = 0,
  val texts: List<String> = emptyList(),
  val other: Boolean = false,
  val name: String = "",
  val defaultName: TextLevelName? = null,
  /** Not user data, so the default is NORMAL, not empty: the spec 0001 exception `ColorLevel` has too. */
  val type: LevelType = LevelType.NORMAL,
  val color: LevelColor? = null,
  val icon: String = "",
  val notify: Boolean = false,
) {
  /** The chosen colour, or the colour of the [type]. */
  val shownColor: LevelColor
    get() = color ?: type.defaultColor

  /** The icon named by [icon]; a blank or unknown name gives [InverterIcon.HELP]. */
  val shownIcon: InverterIcon
    get() = InverterIcon.entries.firstOrNull { it.name == icon } ?: InverterIcon.HELP
}

/** The owner's own text levels of one value; they count only through `TextLevelPicker.ownLevels`. */
@Serializable
data class CustomTextLevels(val on: Boolean = false, val levels: List<TextLevel> = emptyList())

/** The custom text levels of [value]; none stored means an empty [CustomTextLevels]. */
fun Settings.customTextLevels(value: TextValue): CustomTextLevels =
  textLevels[value.name] ?: CustomTextLevels()

fun Settings.withCustomTextLevels(value: TextValue, levels: CustomTextLevels): Settings =
  copy(textLevels = textLevels + (value.name to levels))

/** Texts Home Assistant gives when a sensor has no reading. */
object ModeText {
  fun isNoValue(text: String?): Boolean =
    text == null ||
      text.isBlank() ||
      text.trim().equals("unavailable", ignoreCase = true) ||
      text.trim().equals("unknown", ignoreCase = true)
}
