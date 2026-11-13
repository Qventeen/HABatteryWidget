package com.homeassistant.batterywidget.data

import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** Snapshot of JK-BMS battery metrics fetched from Home Assistant. */
data class BatterySnapshot(
    val socPercent: Double,
    val powerWatts: Double,
    val currentAmps: Double,
    val totalVoltage: Double,
    val deltaVolts: Double,
    val minCellIndex: Int,
    val maxCellIndex: Int,
    val timeStatus: String,
    val cellVoltages: List<Double>,
    val updatedAtMillis: Long,
    val errorMessage: String? = null,
) {
    val hasValidData: Boolean
        get() = socPercent >= 0.0

    val socInt: Int
        get() = socPercent.roundToInt().coerceIn(0, 100)

    /** Serializes the snapshot to a compact JSON string for local widget caching. */
    fun toJsonString(): String {
        val arr = JSONArray()
        cellVoltages.forEach { arr.put(it) }
        return JSONObject().apply {
            put("soc", socPercent)
            put("power", powerWatts)
            put("current", currentAmps)
            put("total_v", totalVoltage)
            put("delta_v", deltaVolts)
            put("min_cell", minCellIndex)
            put("max_cell", maxCellIndex)
            put("time_status", timeStatus)
            put("cells", arr)
            put("updated_at", updatedAtMillis)
            if (errorMessage != null) put("error", errorMessage)
        }.toString()
    }

    companion object {
        /** Restores a cached [BatterySnapshot] from JSON, or null if malformed. */
        fun fromJsonString(raw: String): BatterySnapshot? = try {
            val obj = JSONObject(raw)
            val arr = obj.optJSONArray("cells") ?: JSONArray()
            val cells = buildList {
                for (i in 0 until arr.length()) {
                    add(arr.optDouble(i, 0.0))
                }
            }
            BatterySnapshot(
                socPercent = obj.optDouble("soc", -1.0),
                powerWatts = obj.optDouble("power", 0.0),
                currentAmps = obj.optDouble("current", 0.0),
                totalVoltage = obj.optDouble("total_v", 0.0),
                deltaVolts = obj.optDouble("delta_v", 0.0),
                minCellIndex = obj.optInt("min_cell", 0),
                maxCellIndex = obj.optInt("max_cell", 0),
                timeStatus = obj.optString("time_status", ""),
                cellVoltages = cells,
                updatedAtMillis = obj.optLong("updated_at", 0L),
                errorMessage = obj.optString("error", "").takeIf { it.isNotBlank() },
            )
        } catch (_: Exception) {
            null
        }
    }
}

enum class BatteryChargeMode {
    DISCHARGING,
    CHARGING,
    FULL,
    IDLE,
}

data class BatteryStatusDisplay(
    val mode: BatteryChargeMode,
    val arrow: String,
    val text: String,
)

/** Shared formatting helpers for battery power, time remaining, voltage, and cell delta. */
object BatteryFormatters {

    /** Formats power in W (< 1000 W) or kW (>= 1000 W) without trailing zeros. */
    fun formatPower(wattsAbs: Double): String {
        val cleanAbs = abs(wattsAbs)
        val roundedW = cleanAbs.roundToInt()
        if (roundedW < 1000) return "$roundedW W"
        val kw = (cleanAbs / 1000.0 * 100.0).roundToInt() / 100.0
        val kwStr = if (kw == kw.toInt().toDouble()) {
            kw.toInt().toString()
        } else {
            String.format(Locale.US, "%.2f", kw).trimEnd('0').trimEnd('.')
        }
        return "$kwStr kW"
    }

    /** Formats the primary charge/discharge/time status line matching the HA dashboard card. */
    fun formatStatusLine(snapshot: BatterySnapshot): BatteryStatusDisplay {
        val powerStr = formatPower(snapshot.powerWatts)
        val raw = snapshot.timeStatus
        val extractedTime = raw.substringBefore('|').substringAfter(':', "").trim()
        return when {
            "До 0%" in raw -> {
                val suffix = if (extractedTime.isNotEmpty()) " • До 0%: $extractedTime" else ""
                BatteryStatusDisplay(
                    mode = BatteryChargeMode.DISCHARGING,
                    arrow = "▼",
                    text = "Розряджається $powerStr$suffix",
                )
            }
            "До 100%" in raw -> {
                val suffix = if (extractedTime.isNotEmpty()) " • До 100%: $extractedTime" else ""
                BatteryStatusDisplay(
                    mode = BatteryChargeMode.CHARGING,
                    arrow = "▲",
                    text = "Заряджається $powerStr$suffix",
                )
            }
            "Заряджено" in raw -> {
                BatteryStatusDisplay(
                    mode = BatteryChargeMode.FULL,
                    arrow = "✓",
                    text = "Заряджено 100% • $powerStr",
                )
            }
            snapshot.powerWatts < -20.0 -> {
                BatteryStatusDisplay(
                    mode = BatteryChargeMode.DISCHARGING,
                    arrow = "▼",
                    text = "Розряджається $powerStr",
                )
            }
            snapshot.powerWatts > 20.0 -> {
                BatteryStatusDisplay(
                    mode = BatteryChargeMode.CHARGING,
                    arrow = "▲",
                    text = "Заряджається $powerStr",
                )
            }
            else -> {
                BatteryStatusDisplay(
                    mode = BatteryChargeMode.IDLE,
                    arrow = "⚖",
                    text = "Очікування • $powerStr",
                )
            }
        }
    }

    fun formatTotalVoltage(volts: Double): String =
        String.format(Locale.US, "%.2f V", volts)

    fun formatCurrent(amps: Double): String =
        String.format(Locale.US, "%.1f A", amps)

    fun formatCellVoltage(volts: Double): String =
        String.format(Locale.US, "%.3f", volts)

    /** Returns cell voltage delta in millivolts (mV). */
    fun computeDeltaMv(snapshot: BatterySnapshot): Int {
        if (snapshot.deltaVolts > 0.0) {
            return (snapshot.deltaVolts * 1000.0).roundToInt()
        }
        if (snapshot.cellVoltages.size >= 2) {
            val diff = snapshot.cellVoltages.max() - snapshot.cellVoltages.min()
            return (diff * 1000.0).roundToInt().coerceAtLeast(0)
        }
        return 0
    }

    /** Returns 1-based (minCell, maxCell) indices matching JK-BMS cell highlighting. */
    fun resolveMinMaxCells(snapshot: BatterySnapshot): Pair<Int, Int> {
        val cells = snapshot.cellVoltages
        if (cells.isEmpty()) return 0 to 0
        val minValid = snapshot.minCellIndex in 1..cells.size
        val maxValid = snapshot.maxCellIndex in 1..cells.size
        if (minValid && maxValid && (snapshot.minCellIndex != snapshot.maxCellIndex || computeDeltaMv(snapshot) == 0)) {
            return snapshot.minCellIndex to snapshot.maxCellIndex
        }
        var minIdx = 1
        var maxIdx = 1
        for (i in cells.indices) {
            if (cells[i] < cells[minIdx - 1]) minIdx = i + 1
            if (cells[i] > cells[maxIdx - 1]) maxIdx = i + 1
        }
        return minIdx to maxIdx
    }

    fun formatTimestamp(millis: Long): String {
        if (millis <= 0L) return "--:--"
        return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(millis))
    }
}
