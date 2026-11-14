package io.lumensync.habattery.format

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.lumensync.habattery.R
import io.lumensync.habattery.domain.model.BatteryState
import io.lumensync.habattery.domain.model.Direction
import io.lumensync.habattery.domain.model.RefreshSettings
import io.lumensync.habattery.domain.model.SyncError.BadResponse
import io.lumensync.habattery.domain.model.SyncError.HttpError
import io.lumensync.habattery.domain.model.SyncError.InsecureConnection
import io.lumensync.habattery.domain.model.SyncError.NotConfigured
import io.lumensync.habattery.domain.model.SyncError.Unauthorized
import io.lumensync.habattery.domain.model.SyncError.Unreachable
import io.lumensync.habattery.domain.model.TimeRemaining
import kotlin.time.Duration.Companion.minutes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en")
class FormattersTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun numbers_en() {
        val f = Formatters(context)
        assertEquals("500 W", f.power(500.4))
        assertEquals("1.23 kW", f.power(-1234.0))
        assertEquals("2 kW", f.power(2000.0))
        assertEquals("26.46 V", f.voltage(26.456))
        assertEquals("-12.3 A", f.current(-12.34))
        assertEquals("3.300", f.cellVoltage(3.3))
        assertEquals("—", f.cellVoltage(null))
        assertEquals("55%", f.chargePercent(54.6))
        assertEquals("12 mV", f.deltaMv(12))
    }

    @Test
    @Config(qualifiers = "uk")
    fun numbers_uk() {
        val f = Formatters(context)
        assertEquals("26,46 В", f.voltage(26.456))
        assertEquals("1,23 кВт", f.power(1234.0))
    }

    @Test
    fun everyErrorHasText() {
        val f = Formatters(context)
        listOf(NotConfigured, InsecureConnection, Unauthorized, Unreachable, HttpError(502), BadResponse)
            .forEach { assertTrue(f.error(it).isNotBlank()) }
    }

    @Test
    fun httpErrorShowsCode() = assertTrue(Formatters(context).error(HttpError(502)).contains("502"))

    @Test
    fun refreshMode() {
        val f = Formatters(context)
        assertTrue(f.refreshMode(RefreshSettings(true, 30)).contains("30"))
        assertEquals(context.getString(R.string.refresh_mode_manual), f.refreshMode(RefreshSettings()))
    }

    @Test
    fun status_combinesStateAndTime() {
        val f = Formatters(context)
        val time = TimeRemaining(Direction.TO_FULL, 90.minutes)
        val text = f.status(BatteryState.CHARGING, 500.0, time)
        assertTrue(text, text.startsWith("Charging 500 W"))
        assertTrue(text, text.contains(" · "))
        assertEquals("Full", f.status(BatteryState.FULL, null, null))
        assertEquals("Discharging 500 W", f.status(BatteryState.DISCHARGING, 500.0, null))
    }

    @Test
    fun duration_hoursAndMinutes() {
        val f = Formatters(context)
        assertTrue(f.duration(90.minutes).contains("30"))
        assertTrue(f.duration(45.minutes).contains("45"))
    }
}
