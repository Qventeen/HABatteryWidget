package io.lumensync.habattery.domain

import io.lumensync.habattery.domain.model.BatteryState
import kotlin.math.roundToInt

object FullChargeRule {
  const val REARM_PERCENT = 98

  data class Decision(val notify: Boolean, val notified: Boolean)

  fun evaluate(
    state: BatteryState,
    chargePercent: Double,
    enabled: Boolean,
    alreadyNotified: Boolean,
  ): Decision =
    if (state == BatteryState.FULL) {
      Decision(notify = enabled && !alreadyNotified, notified = if (enabled) true else alreadyNotified)
    } else {
      Decision(
        notify = false,
        notified = if (chargePercent.roundToInt() <= REARM_PERCENT) false else alreadyNotified,
      )
    }
}
