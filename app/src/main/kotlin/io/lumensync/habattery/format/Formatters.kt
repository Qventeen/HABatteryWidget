package io.lumensync.habattery.format

import android.content.Context
import android.icu.text.MeasureFormat
import android.icu.util.Measure
import android.icu.util.MeasureUnit
import android.text.format.DateFormat
import android.text.format.DateUtils
import io.lumensync.habattery.R
import io.lumensync.habattery.domain.model.BatteryState
import io.lumensync.habattery.domain.model.Direction
import io.lumensync.habattery.domain.model.RefreshSettings
import io.lumensync.habattery.domain.model.SyncError
import io.lumensync.habattery.domain.model.TimeRemaining
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

    fun chargePercent(percent: Double): String =
        context.getString(R.string.charge_percent, percent.roundToInt())

    fun duration(d: Duration): String {
        val totalMinutes = d.inWholeMinutes
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        val format = MeasureFormat.getInstance(locale, MeasureFormat.FormatWidth.SHORT)
        return if (hours == 0L) {
            format.format(Measure(minutes, MeasureUnit.MINUTE))
        } else {
            format.formatMeasures(Measure(hours, MeasureUnit.HOUR), Measure(minutes, MeasureUnit.MINUTE))
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

    fun status(state: BatteryState, powerW: Double?, time: TimeRemaining?): String {
        val base = when (state) {
            BatteryState.CHARGING -> context.getString(R.string.status_charging, powerOrMissing(powerW))
            BatteryState.DISCHARGING -> context.getString(R.string.status_discharging, powerOrMissing(powerW))
            BatteryState.IDLE -> context.getString(R.string.status_idle)
            BatteryState.FULL -> context.getString(R.string.status_full)
            BatteryState.UNKNOWN -> context.getString(R.string.status_unknown)
        }
        if (time == null) return base
        val timeText = when (time.direction) {
            Direction.TO_FULL -> context.getString(R.string.time_to_full, duration(time.duration))
            Direction.TO_EMPTY -> context.getString(R.string.time_to_empty, duration(time.duration))
        }
        return context.getString(R.string.status_with_time, base, timeText)
    }

    fun error(e: SyncError): String = when (e) {
        SyncError.NotConfigured -> context.getString(R.string.error_not_configured)
        SyncError.Unauthorized -> context.getString(R.string.error_unauthorized)
        SyncError.Unreachable -> context.getString(R.string.error_unreachable)
        is SyncError.HttpError -> context.getString(R.string.error_http, e.code)
        SyncError.BadResponse -> context.getString(R.string.error_bad_response)
    }

    fun refreshMode(r: RefreshSettings): String {
        val minutes = r.intervalMinutes
        return if (r.auto && minutes != null) {
            context.resources.getQuantityString(R.plurals.refresh_mode_auto, minutes, minutes)
        } else {
            context.getString(R.string.refresh_mode_manual)
        }
    }

    private fun powerOrMissing(watts: Double?): String =
        if (watts == null) context.getString(R.string.value_missing) else power(watts)

    private companion object {
        const val KILO = 1000.0

        /** "Jan 14, 11:59 PM" style; the year only when it is not this year. */
        const val DATE_AND_TIME =
            DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_MONTH
    }
}
