package io.lumensync.habattery.widget

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import io.lumensync.habattery.R
import io.lumensync.habattery.domain.model.InverterIcon

/** The tintable vector for this icon. */
@DrawableRes
fun InverterIcon.drawable(): Int =
  when (this) {
    InverterIcon.TOWER -> R.drawable.ic_inv_tower
    InverterIcon.LOCATION_CITY -> R.drawable.ic_inv_location_city
    InverterIcon.ELECTRICAL_SERVICES -> R.drawable.ic_inv_electrical_services
    InverterIcon.POWER_INPUT -> R.drawable.ic_inv_power_input
    InverterIcon.ELECTRIC_METER -> R.drawable.ic_inv_electric_meter
    InverterIcon.FACTORY -> R.drawable.ic_inv_factory
    InverterIcon.APARTMENT -> R.drawable.ic_inv_apartment
    InverterIcon.OFFLINE_BOLT -> R.drawable.ic_inv_offline_bolt
    InverterIcon.BOLT -> R.drawable.ic_inv_bolt
    InverterIcon.ELECTRIC_BOLT -> R.drawable.ic_inv_electric_bolt
    InverterIcon.BATTERY_CHARGING_FULL -> R.drawable.ic_inv_battery_charging_full
    InverterIcon.BATTERY_FULL -> R.drawable.ic_inv_battery_full
    InverterIcon.BATTERY_5_BAR -> R.drawable.ic_inv_battery_5_bar
    InverterIcon.BATTERY_ANDROID_FULL -> R.drawable.ic_inv_battery_android_full
    InverterIcon.BATTERY_HORIZ_075 -> R.drawable.ic_inv_battery_horiz_075
    InverterIcon.ENERGY_SAVINGS_LEAF -> R.drawable.ic_inv_energy_savings_leaf
    InverterIcon.NEST_ECO_LEAF -> R.drawable.ic_inv_nest_eco_leaf
    InverterIcon.ENERGY_PROGRAM_SAVING -> R.drawable.ic_inv_energy_program_saving
    InverterIcon.SOLAR_POWER -> R.drawable.ic_inv_solar_power
    InverterIcon.HELP -> R.drawable.ic_inv_help
    InverterIcon.QUESTION_MARK -> R.drawable.ic_inv_question_mark
    InverterIcon.DEVICE_UNKNOWN -> R.drawable.ic_inv_device_unknown
    InverterIcon.PENDING -> R.drawable.ic_inv_pending
    InverterIcon.INFO -> R.drawable.ic_inv_info
    InverterIcon.POWER -> R.drawable.ic_inv_power
    InverterIcon.HOME -> R.drawable.ic_inv_home
    InverterIcon.HOUSE -> R.drawable.ic_inv_house
    InverterIcon.COTTAGE -> R.drawable.ic_inv_cottage
    InverterIcon.HOME_WORK -> R.drawable.ic_inv_home_work
    InverterIcon.HOME_LIGHTNING_BOLT -> R.drawable.ic_inv_home_lightning_bolt
    InverterIcon.OUTLET -> R.drawable.ic_inv_outlet
    InverterIcon.POWER_SETTINGS_NEW -> R.drawable.ic_inv_power_settings_new
    InverterIcon.RESTART_ALT -> R.drawable.ic_inv_restart_alt
    InverterIcon.MODE_STANDBY -> R.drawable.ic_inv_mode_standby
    InverterIcon.BEDTIME -> R.drawable.ic_inv_bedtime
    InverterIcon.ALT_ROUTE -> R.drawable.ic_inv_alt_route
    InverterIcon.CALL_SPLIT -> R.drawable.ic_inv_call_split
    InverterIcon.SWAP_HORIZ -> R.drawable.ic_inv_swap_horiz
    InverterIcon.CHARGER -> R.drawable.ic_inv_charger
    InverterIcon.BATTERY_CHARGING_90 -> R.drawable.ic_inv_battery_charging_90
    InverterIcon.EV_CHARGER -> R.drawable.ic_inv_ev_charger
    InverterIcon.ERROR -> R.drawable.ic_inv_error
    InverterIcon.REPORT -> R.drawable.ic_inv_report
    InverterIcon.WARNING -> R.drawable.ic_inv_warning
    InverterIcon.DANGEROUS -> R.drawable.ic_inv_dangerous  }

/** A short name for screen readers and the icon picker. */
@StringRes
fun InverterIcon.label(): Int =
  when (this) {
    InverterIcon.TOWER -> R.string.inv_icon_tower
    InverterIcon.LOCATION_CITY -> R.string.inv_icon_location_city
    InverterIcon.ELECTRICAL_SERVICES -> R.string.inv_icon_electrical_services
    InverterIcon.POWER_INPUT -> R.string.inv_icon_power_input
    InverterIcon.ELECTRIC_METER -> R.string.inv_icon_electric_meter
    InverterIcon.FACTORY -> R.string.inv_icon_factory
    InverterIcon.APARTMENT -> R.string.inv_icon_apartment
    InverterIcon.OFFLINE_BOLT -> R.string.inv_icon_offline_bolt
    InverterIcon.BOLT -> R.string.inv_icon_bolt
    InverterIcon.ELECTRIC_BOLT -> R.string.inv_icon_electric_bolt
    InverterIcon.BATTERY_CHARGING_FULL -> R.string.inv_icon_battery_charging_full
    InverterIcon.BATTERY_FULL -> R.string.inv_icon_battery_full
    InverterIcon.BATTERY_5_BAR -> R.string.inv_icon_battery_5_bar
    InverterIcon.BATTERY_ANDROID_FULL -> R.string.inv_icon_battery_android_full
    InverterIcon.BATTERY_HORIZ_075 -> R.string.inv_icon_battery_horiz_075
    InverterIcon.ENERGY_SAVINGS_LEAF -> R.string.inv_icon_energy_savings_leaf
    InverterIcon.NEST_ECO_LEAF -> R.string.inv_icon_nest_eco_leaf
    InverterIcon.ENERGY_PROGRAM_SAVING -> R.string.inv_icon_energy_program_saving
    InverterIcon.SOLAR_POWER -> R.string.inv_icon_solar_power
    InverterIcon.HELP -> R.string.inv_icon_help
    InverterIcon.QUESTION_MARK -> R.string.inv_icon_question_mark
    InverterIcon.DEVICE_UNKNOWN -> R.string.inv_icon_device_unknown
    InverterIcon.PENDING -> R.string.inv_icon_pending
    InverterIcon.INFO -> R.string.inv_icon_info
    InverterIcon.POWER -> R.string.inv_icon_power
    InverterIcon.HOME -> R.string.inv_icon_home
    InverterIcon.HOUSE -> R.string.inv_icon_house
    InverterIcon.COTTAGE -> R.string.inv_icon_cottage
    InverterIcon.HOME_WORK -> R.string.inv_icon_home_work
    InverterIcon.HOME_LIGHTNING_BOLT -> R.string.inv_icon_home_lightning_bolt
    InverterIcon.OUTLET -> R.string.inv_icon_outlet
    InverterIcon.POWER_SETTINGS_NEW -> R.string.inv_icon_power_settings_new
    InverterIcon.RESTART_ALT -> R.string.inv_icon_restart_alt
    InverterIcon.MODE_STANDBY -> R.string.inv_icon_mode_standby
    InverterIcon.BEDTIME -> R.string.inv_icon_bedtime
    InverterIcon.ALT_ROUTE -> R.string.inv_icon_alt_route
    InverterIcon.CALL_SPLIT -> R.string.inv_icon_call_split
    InverterIcon.SWAP_HORIZ -> R.string.inv_icon_swap_horiz
    InverterIcon.CHARGER -> R.string.inv_icon_charger
    InverterIcon.BATTERY_CHARGING_90 -> R.string.inv_icon_battery_charging_90
    InverterIcon.EV_CHARGER -> R.string.inv_icon_ev_charger
    InverterIcon.ERROR -> R.string.inv_icon_error
    InverterIcon.REPORT -> R.string.inv_icon_report
    InverterIcon.WARNING -> R.string.inv_icon_warning
    InverterIcon.DANGEROUS -> R.string.inv_icon_dangerous  }
