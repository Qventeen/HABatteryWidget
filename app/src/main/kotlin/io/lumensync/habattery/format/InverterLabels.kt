package io.lumensync.habattery.format

import androidx.annotation.StringRes
import io.lumensync.habattery.R
import io.lumensync.habattery.domain.model.IconSlot
import io.lumensync.habattery.domain.model.InverterMode

/** The word for a mode in the notification and the strip. */
@StringRes
fun modeWordRes(mode: InverterMode): Int =
  when (mode) {
    InverterMode.POWER_ON -> R.string.mode_word_power_on
    InverterMode.STANDBY -> R.string.mode_word_standby
    InverterMode.MAINS -> R.string.mode_word_mains
    InverterMode.OFF_GRID -> R.string.mode_word_off_grid
    InverterMode.BYPASS -> R.string.mode_word_bypass
    InverterMode.CHARGING -> R.string.mode_word_charging
    InverterMode.FAULT -> R.string.mode_word_fault
  }

/** The name of a strip place in the Icons section. */
@StringRes
fun iconSlotRes(slot: IconSlot): Int =
  when (slot) {
    IconSlot.MODE_POWER_ON -> R.string.inv_slot_mode_power_on
    IconSlot.MODE_STANDBY -> R.string.inv_slot_mode_standby
    IconSlot.MODE_MAINS -> R.string.inv_slot_mode_mains
    IconSlot.MODE_OFF_GRID -> R.string.inv_slot_mode_off_grid
    IconSlot.MODE_BYPASS -> R.string.inv_slot_mode_bypass
    IconSlot.MODE_CHARGING -> R.string.inv_slot_mode_charging
    IconSlot.MODE_FAULT -> R.string.inv_slot_mode_fault
    IconSlot.MODE_OTHER -> R.string.inv_slot_mode_other
    IconSlot.GRID_VOLTAGE -> R.string.inv_slot_grid_voltage
    IconSlot.OUTPUT_VOLTAGE -> R.string.inv_slot_output_voltage
  }
