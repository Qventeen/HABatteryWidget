package io.lumensync.habattery.format

import android.content.Context
import android.icu.util.TimeZone as IcuTimeZone
import androidx.test.core.app.ApplicationProvider
import io.lumensync.habattery.R
import io.lumensync.habattery.domain.model.SyncError.BadResponse
import io.lumensync.habattery.domain.model.SyncError.HttpError
import io.lumensync.habattery.domain.model.SyncError.NotConfigured
import io.lumensync.habattery.domain.model.SyncError.Unauthorized
import io.lumensync.habattery.domain.model.SyncError.Unreachable
import java.util.TimeZone
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en")
class FormattersTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private val zone: TimeZone = TimeZone.getDefault()

    @Before fun utc() = TimeZone.setDefault(TimeZone.getTimeZone("UTC"))

    @After fun restoreZone() = TimeZone.setDefault(zone)

    @Test
    fun numbers_en() {
        val f = Formatters(context)
        assertEquals("500 W", f.power(500.4))
        assertEquals("1.23 kW", f.power(-1234.0))
        assertEquals("2 kW", f.power(2000.0))
        assertEquals("26.46 V", f.voltage(26.456))
        assertEquals("-12.3 A", f.current(-12.34))
        assertEquals("55%", f.chargePercent(54.6))
        assertEquals("12 mV", f.deltaMv(12))
    }

    @Test
    fun newValues_en() {
        val f = Formatters(context)
        assertEquals("25 °C", f.temperature(25.0))
        // Whole degrees: a decimal or negative value fits the 90 dp pill.
        assertEquals("25 °C", f.temperature(25.35))
        assertEquals("-13 °C", f.temperature(-12.6))
        assertEquals("0 °C", f.temperature(-0.4))
        assertEquals("157.0 Ah", f.ampHours(157.0))
        // number() rounds half-even, so the brief's tie value 3.3125 gives 3.312; use a clear case.
        assertEquals("3.313 V", f.cellVoltage(3.3126))
    }

    @Test
    fun wholeVolts() {
        val f = Formatters(context)
        assertEquals("230 V", f.wholeVolts(230.0))
        // Half-even, as ShownNumber rounds the inverter voltages.
        assertEquals("206 V", f.wholeVolts(206.5))
    }

    @Test
    fun power_roundsBeforePickingUnit() {
        val f = Formatters(context)
        assertEquals("999 W", f.power(999.4))
        assertEquals("1 kW", f.power(999.5))
        assertEquals("1 kW", f.power(999.6))
    }

    @Test
    @Config(qualifiers = "uk")
    fun numbers_uk() {
        val f = Formatters(context)
        assertEquals("26,46 В", f.voltage(26.456))
        assertEquals("230 В", f.wholeVolts(230.0))
        assertEquals("1,23 кВт", f.power(1234.0))
    }

    @Test
    fun everyErrorHasText() {
        val f = Formatters(context)
        listOf(NotConfigured, Unauthorized, Unreachable, HttpError(502), BadResponse)
            .forEach { assertTrue(f.error(it).isNotBlank()) }
    }

    @Test
    fun httpErrorShowsCode() = assertTrue(Formatters(context).error(HttpError(502)).contains("502"))

    @Test
    fun updatedAt_today_showsTimeOnly() {
        val text = Formatters(context).updatedAt(MORNING, nowMillis = MORNING + 4 * HOUR)
        assertTrue(text, text.contains("8:00"))
        assertFalse(text, text.contains("Jan"))
    }

    /** Owner, 2026-11-16: an earlier day shows the date only. */
    @Test
    fun updatedAt_olderDay_showsTheDateOnly() {
        val f = Formatters(context)
        assertEquals("Jan 13", f.updatedAt(MORNING - 2.days.inWholeMilliseconds, nowMillis = MORNING))
        // One minute before midnight is already "yesterday".
        assertEquals("Jan 14", f.updatedAt(MORNING - 8 * HOUR - 60_000L, nowMillis = MORNING))
    }

    /**
     * The date is written in the zone that picks the day. ICU keeps its own default zone from its
     * first use, and on the JVM `TimeZone.setDefault` does not clear it.
     */
    @Test
    fun updatedAt_olderDay_ignoresIcusOwnDefaultZone() =
        withIcuDefaultZone("Asia/Tokyo") {
            assertEquals("Jan 14", Formatters(context).updatedAt(MORNING - 8 * HOUR - 60_000L, nowMillis = MORNING))
        }

    @Test
    fun updatedAt_otherYear_showsTheYear() =
        assertEquals("Dec 31, 2026", Formatters(context).updatedAt(MORNING - 15.days.inWholeMilliseconds, nowMillis = MORNING))

    /** The month is in the app's language, not the device's default. */
    @Test
    @Config(qualifiers = "uk")
    fun updatedAt_olderDay_uk_showsAUkrainianMonth() {
        val text = Formatters(context).updatedAt(MORNING - 2.days.inWholeMilliseconds, nowMillis = MORNING)
        assertTrue(text, text.contains("13") && text.contains("січ"))
    }

    @Test
    fun duration_hoursAndMinutes() {
        val f = Formatters(context)
        assertTrue(f.duration(90.minutes).contains("30"))
        assertTrue(f.duration(45.minutes).contains("45"))
    }

    @Test
    fun widgetNumbers_en() {
        val f = Formatters(context)
        assertEquals("4.16 kWh", f.kilowattHours(4.1605))
        assertEquals("55", f.socNumber(55))
        assertEquals("100", f.socNumber(100))
    }

    /** Owner, 2026-11-16: Android's narrow format. */
    @Test
    fun duration_underOneDay_hoursAndMinutes() {
        val f = Formatters(context)
        assertEquals("59m", f.duration(59.minutes))
        assertEquals("17h 16m", f.duration((17 * 60 + 16).minutes))
        assertEquals("23h 59m", f.duration((23 * 60 + 59).minutes))
    }

    @Test
    fun duration_fromOneDay_daysAndHours() {
        val f = Formatters(context)
        assertEquals("1d 0h", f.duration(24.hours))
        assertEquals("2d 7h", f.duration(55.hours + 20.minutes))
    }

    @Test
    @Config(qualifiers = "uk")
    fun duration_days_uk_hasNoMinutes() {
        val f = Formatters(context)
        val text = f.duration(55.hours + 20.minutes)
        val minuteUnit = f.duration(1.minutes).filterNot { it.isDigit() }.trim()
        assertTrue(text, text.contains("2") && text.contains("7") && !text.contains("20"))
        assertFalse(text, text.contains(minuteUnit))
    }

    /** ICU's setter is hidden from apps; clearing it makes ICU read the JVM's zone again. */
    private fun withIcuDefaultZone(id: String, block: () -> Unit) {
        val setIcuDefault = IcuTimeZone::class.java.getMethod("setICUDefault", IcuTimeZone::class.java)
        setIcuDefault.invoke(null, IcuTimeZone.getTimeZone(id))
        try {
            block()
        } finally {
            setIcuDefault.invoke(null, null)
        }
    }

    private companion object {
        /** 2027-01-15 08:00 UTC. */
        const val MORNING = 1_800_000_000_000L
        const val HOUR = 3_600_000L
    }
}
