package com.homeassistant.batterywidget.notifications

import com.homeassistant.batterywidget.data.BatterySnapshot
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import org.junit.Test

class BatteryFullNotificationTest {

    @Test
    fun evaluate_notifiesOnceAt100PercentAndRearmsBelowThreshold() {
        val fullSnapshot = snapshot(soc = 100.0, timeStatus = "Заряджено")

        // Disabled -> never notifies
        val disabled = FullChargeNotificationEvaluator.evaluate(
            snapshot = fullSnapshot,
            notifyEnabled = false,
            alreadyNotified = false,
        )
        assertFalse(disabled.shouldNotify)
        assertFalse(disabled.nextNotifiedState)

        // First 100% detection while enabled -> notifies once and latches
        val firstFull = FullChargeNotificationEvaluator.evaluate(
            snapshot = fullSnapshot,
            notifyEnabled = true,
            alreadyNotified = false,
        )
        assertTrue(firstFull.shouldNotify)
        assertTrue(firstFull.nextNotifiedState)

        // Subsequent poll at 100% -> does not notify again
        val repeatedFull = FullChargeNotificationEvaluator.evaluate(
            snapshot = fullSnapshot,
            notifyEnabled = true,
            alreadyNotified = true,
        )
        assertFalse(repeatedFull.shouldNotify)
        assertTrue(repeatedFull.nextNotifiedState)

        // Minor drop to 99% -> keeps latch armed (avoids float bounce spam)
        val minorDrop = FullChargeNotificationEvaluator.evaluate(
            snapshot = snapshot(soc = 99.0, timeStatus = "🔴 До 0%: 40г | 100W"),
            notifyEnabled = true,
            alreadyNotified = true,
        )
        assertFalse(minorDrop.shouldNotify)
        assertTrue(minorDrop.nextNotifiedState)

        // Discharge to 98% -> re-arms for next full charge
        val rearmed = FullChargeNotificationEvaluator.evaluate(
            snapshot = snapshot(soc = 98.0, timeStatus = "🔴 До 0%: 39г | 150W"),
            notifyEnabled = true,
            alreadyNotified = true,
        )
        assertFalse(rearmed.shouldNotify)
        assertFalse(rearmed.nextNotifiedState)

        // Reaches 100% again -> notifies once more
        val secondCycle = FullChargeNotificationEvaluator.evaluate(
            snapshot = snapshot(soc = 99.6, timeStatus = "🟢 До 100%: 1хв | 300W"),
            notifyEnabled = true,
            alreadyNotified = rearmed.nextNotifiedState,
        )
        assertTrue(secondCycle.shouldNotify)
        assertTrue(secondCycle.nextNotifiedState)
    }

    @Test
    fun evaluate_ignoresInvalidOrErrorSnapshots() {
        val errorFull = snapshot(soc = 100.0, timeStatus = "Заряджено", error = "Timeout")
        val errEval = FullChargeNotificationEvaluator.evaluate(
            snapshot = errorFull,
            notifyEnabled = true,
            alreadyNotified = false,
        )
        assertFalse(errEval.shouldNotify)
        assertFalse(errEval.nextNotifiedState)

        val invalidLow = snapshot(soc = -1.0, timeStatus = "", cells = emptyList())
        val invalidEval = FullChargeNotificationEvaluator.evaluate(
            snapshot = invalidLow,
            notifyEnabled = true,
            alreadyNotified = true,
        )
        assertFalse(invalidEval.shouldNotify)
        assertTrue(invalidEval.nextNotifiedState)
    }

    @Test
    fun isBatteryFull_recognizesFullStatusModeEvenBeforeSocReaches100() {
        val statusFull = snapshot(soc = 99.2, timeStatus = "✅ Заряджено | 50W")
        assertTrue(FullChargeNotificationEvaluator.isBatteryFull(statusFull))
        assertEquals(
            FullChargeEvalResult(shouldNotify = true, nextNotifiedState = true),
            FullChargeNotificationEvaluator.evaluate(
                snapshot = statusFull,
                notifyEnabled = true,
                alreadyNotified = false,
            ),
        )
    }

    private fun snapshot(
        soc: Double,
        timeStatus: String,
        error: String? = null,
        cells: List<Double> = List(8) { 3.45 },
    ) = BatterySnapshot(
        socPercent = soc,
        powerWatts = 120.0,
        currentAmps = 4.2,
        totalVoltage = 27.60,
        deltaVolts = 0.003,
        minCellIndex = 1,
        maxCellIndex = 4,
        timeStatus = timeStatus,
        cellVoltages = cells,
        updatedAtMillis = 1794300000000L,
        errorMessage = error,
    )
}
