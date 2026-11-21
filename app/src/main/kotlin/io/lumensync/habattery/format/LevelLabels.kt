package io.lumensync.habattery.format

import android.content.res.Resources
import androidx.annotation.StringRes
import io.lumensync.habattery.R
import io.lumensync.habattery.domain.LevelSpan
import io.lumensync.habattery.domain.model.ColorLevel
import io.lumensync.habattery.domain.model.LevelName
import io.lumensync.habattery.domain.model.LevelSide
import io.lumensync.habattery.domain.model.LevelType
import io.lumensync.habattery.domain.model.LevelValue
import java.util.Locale

/** The string of a level's default name. */
@StringRes
fun levelNameRes(name: LevelName): Int =
  when (name) {
    LevelName.LOW -> R.string.level_name_low
    LevelName.GETTING_LOW -> R.string.level_name_getting_low
    LevelName.NORMAL -> R.string.level_name_normal
    LevelName.FULL -> R.string.level_name_full
    LevelName.FREEZING -> R.string.level_name_freezing
    LevelName.COOL -> R.string.level_name_cool
    LevelName.WARM -> R.string.level_name_warm
    LevelName.HOT -> R.string.level_name_hot
    LevelName.BALANCED -> R.string.level_name_balanced
    LevelName.HIGH -> R.string.level_name_high
    LevelName.VERY_HIGH -> R.string.level_name_very_high
    LevelName.LIGHT -> R.string.level_name_light
    LevelName.NO_GRID -> R.string.level_name_no_grid
    LevelName.NO_POWER -> R.string.level_name_no_power
  }

/** The short unit of a value in a level's place, such as "%". */
@StringRes
fun unitLabel(value: LevelValue): Int =
  when (value) {
    LevelValue.SOC -> R.string.colors_unit_percent
    LevelValue.VOLTAGE,
    LevelValue.GRID_VOLTAGE,
    LevelValue.OUTPUT_VOLTAGE -> R.string.colors_unit_volts
    LevelValue.CHARGE_CURRENT,
    LevelValue.DISCHARGE_CURRENT -> R.string.colors_unit_amps
    LevelValue.DELTA -> R.string.colors_unit_millivolts
    LevelValue.BATTERY_TEMPERATURE,
    LevelValue.MOSFET_TEMPERATURE -> R.string.colors_unit_celsius
  }

/** The type word, which a level with no name shows instead. */
@StringRes
fun typeLabel(type: LevelType): Int =
  when (type) {
    LevelType.CRITICAL -> R.string.level_type_critical
    LevelType.WARNING -> R.string.level_type_warning
    LevelType.NORMAL -> R.string.level_type_normal
    LevelType.INFO -> R.string.level_type_info
  }

/**
 * The type word on a mode's type tag. It is short, so the tag leaves room for the mode word on a
 * narrow screen; the type menu shows [typeLabel].
 */
@StringRes
fun typeTagLabel(type: LevelType): Int =
  when (type) {
    LevelType.CRITICAL -> R.string.level_type_critical_short
    LevelType.WARNING -> R.string.level_type_warning_short
    LevelType.NORMAL -> R.string.level_type_normal_short
    LevelType.INFO -> R.string.level_type_info_short
  }

/**
 * The level's own name, else its default name; `null` when it has neither. The tag in Levels and
 * alerts, the editor and the notification title all read the name here.
 */
fun ColorLevel.nameOrNull(resources: Resources): String? =
  name.ifBlank { defaultName?.let { resources.getString(levelNameRes(it)) }.orEmpty() }
    .ifBlank { null }

/** [nameOrNull], else the type word, so a level always has something to show. */
fun ColorLevel.shownName(resources: Resources): String =
  nameOrNull(resources) ?: resources.getString(typeLabel(type))

/**
 * The span's place with its [unit], such as "↓20 %" or "↕41–99 %"; `null` for `Anything`. The tags,
 * the editor's middle row and the notification title read the place here.
 */
fun LevelSpan.placeWithUnit(
  decimals: Int,
  unit: String,
  locale: Locale,
  side: LevelSide,
  resources: Resources,
): String? =
  placeText(decimals, locale, side)?.let { resources.getString(R.string.colors_place, it, unit) }

/** The value's label in a notification title, such as "Charge". */
@StringRes
fun notificationValueRes(value: LevelValue): Int =
  when (value) {
    LevelValue.SOC -> R.string.notification_value_soc
    LevelValue.VOLTAGE -> R.string.notification_value_voltage
    LevelValue.CHARGE_CURRENT -> R.string.notification_value_charge_current
    LevelValue.DISCHARGE_CURRENT -> R.string.notification_value_discharge_current
    LevelValue.DELTA -> R.string.notification_value_delta
    LevelValue.BATTERY_TEMPERATURE -> R.string.notification_value_battery_temperature
    LevelValue.MOSFET_TEMPERATURE -> R.string.notification_value_mosfet_temperature
    LevelValue.GRID_VOLTAGE -> R.string.notification_value_grid_voltage
    LevelValue.OUTPUT_VOLTAGE -> R.string.notification_value_output_voltage
  }
