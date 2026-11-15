package io.lumensync.habattery.format

import android.content.Context
import android.icu.text.MeasureFormat
import android.icu.util.Measure
import android.icu.util.MeasureUnit
import android.text.format.DateFormat
import android.text.format.DateUtils
import io.lumensync.habattery.R
import io.lumensync.habattery.domain.model.SyncError
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.time.Duration

/** Turns domain values into localized text. Every visible word comes from string resources. */
class Formatters(private val context: Context) {
    private val locale: Locale get() = context.resources.configuration.locales[0]

    private fun number(value: Double, minFraction: Int, maxFraction: Int): String =
        NumberFormat.getInstance(locale).apply {
            minimumFractionDigits = minFraction
            maximumFractionDigits = maxFraction
        }.format(value)

    fun power(watts: Double): String {
        val absolute = abs(watts)
        // Round first, so 999.6 W becomes "1 kW" and not "1,000 W".
        val rounded = Math.round(absolute).toDouble()
        return if (rounded < KILO) {
            context.getString(R.string.unit_watts, number(rounded, 0, 0))
        } else {
            context.getString(R.string.unit_kilowatts, number(absolute / KILO, 0, 2))
        }
    }

    fun voltage(volts: Double): String =
        context.getString(R.string.unit_volts, number(volts, 2, 2))

    fun current(amps: Double): String =
        context.getString(R.string.unit_amps, number(amps, 1, 1))

    fun deltaMv(mv: Int): String = context.getString(R.string.unit_millivolts, number(mv.toDouble(), 0, 0))

    /** Whole degrees, as the widget design shows them. Rounded first, so -0.4 is "0", not "-0". */
    fun temperature(celsius: Double): String =
        context.getString(R.string.unit_celsius, number(Math.round(celsius).toDouble(), 0, 0))

    fun kilowattHours(kwh: Double): String =
        context.getString(R.string.unit_kilowatt_hours, number(kwh, 2, 2))

    /** The SOC number inside the ring, without "%". */
    fun socNumber(percent: Int): String = number(percent.toDouble(), 0, 0)

    fun ampHours(ah: Double): String =
        context.getString(R.string.unit_amp_hours, number(ah, 1, 1))

    fun cellVoltage(volts: Double): String =
        context.getString(R.string.unit_volts, number(volts, 3, 3))

    fun chargePercent(percent: Double): String =
        context.getString(R.string.charge_percent, percent.roundToInt())

    /** "45 min", "17 hr, 16 min"; from 24 h days and hours ("2 days, 7 hr"), minutes dropped. */
    fun duration(d: Duration): String {
        val totalMinutes = d.inWholeMinutes
        val days = totalMinutes / MINUTES_PER_DAY
        val hours = totalMinutes % MINUTES_PER_DAY / 60
        val minutes = totalMinutes % 60
        val format = MeasureFormat.getInstance(locale, MeasureFormat.FormatWidth.SHORT)
        return when {
            days > 0L ->
                format.formatMeasures(Measure(days, MeasureUnit.DAY), Measure(hours, MeasureUnit.HOUR))
            hours > 0L ->
                format.formatMeasures(Measure(hours, MeasureUnit.HOUR), Measure(minutes, MeasureUnit.MINUTE))
            else -> format.format(Measure(minutes, MeasureUnit.MINUTE))
        }
    }

    /** When a reading was taken: the time for today, else the date and the time. */
    fun updatedAt(millis: Long, nowMillis: Long = System.currentTimeMillis()): String =
        if (localDate(millis) == localDate(nowMillis)) {
            DateFormat.getTimeFormat(context).format(Date(millis))
        } else {
            DateUtils.formatDateTime(context, millis, DATE_AND_TIME)
        }

    private fun localDate(millis: Long): LocalDate =
        Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()

    fun error(e: SyncError): String = when (e) {
        SyncError.NotConfigured -> context.getString(R.string.error_not_configured)
        SyncError.Unauthorized -> context.getString(R.string.error_unauthorized)
        SyncError.Unreachable -> context.getString(R.string.error_unreachable)
        is SyncError.HttpError -> context.getString(R.string.error_http, e.code)
        SyncError.BadResponse -> context.getString(R.string.error_bad_response)
    }

    private companion object {
        const val KILO = 1000.0
        const val MINUTES_PER_DAY = 24 * 60L

        /** "Jan 14, 11:59 PM" style; the year only when it is not this year. */
        const val DATE_AND_TIME =
            DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_MONTH
    }
}
