package io.lumensync.habattery.domain

import io.lumensync.habattery.domain.FullChargeRule.Decision
import io.lumensync.habattery.domain.model.BatteryState.DISCHARGING
import io.lumensync.habattery.domain.model.BatteryState.FULL
import org.junit.Assert.assertEquals
import org.junit.Test

class FullChargeRuleTest {
  @Test
  fun notifiesOnceWhenFull() {
    assertEquals(
      Decision(true, true),
      FullChargeRule.evaluate(FULL, 100.0, enabled = true, alreadyNotified = false),
    )
    assertEquals(
      Decision(false, true),
      FullChargeRule.evaluate(FULL, 100.0, enabled = true, alreadyNotified = true),
    )
  }

  @Test
  fun disabledNeverNotifies() =
    assertEquals(
      Decision(false, false),
      FullChargeRule.evaluate(FULL, 100.0, enabled = false, alreadyNotified = false),
    )

  @Test
  fun rearmsAt98() {
    assertEquals(Decision(false, false), FullChargeRule.evaluate(DISCHARGING, 98.4, true, true))
    assertEquals(Decision(false, true), FullChargeRule.evaluate(DISCHARGING, 99.0, true, true))
  }
}
