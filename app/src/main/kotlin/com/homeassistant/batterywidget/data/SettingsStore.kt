package com.homeassistant.batterywidget.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/** Home Assistant API connection settings. */
data class HaConnectionConfig(
    val primaryUrl: String,
    val fallbackUrl: String,
    val accessToken: String,
)

/** Widget background update mode and manually configurable interval. */
data class WidgetRefreshConfig(
    val autoRefreshEnabled: Boolean = true,
    val intervalMinutes: Int = DEFAULT_INTERVAL_MINUTES,
) {
    val sanitizedIntervalMinutes: Int
        get() = intervalMinutes.coerceIn(MIN_INTERVAL_MINUTES, MAX_INTERVAL_MINUTES)

    companion object {
        const val MIN_INTERVAL_MINUTES = 1
        const val MAX_INTERVAL_MINUTES = 1440
        const val DEFAULT_INTERVAL_MINUTES = 15
    }
}

/** Notification preference and deduplication state for 100% battery charge alerts. */
data class BatteryNotificationConfig(
    val notifyOnFullEnabled: Boolean = false,
    val fullChargeNotified: Boolean = false,
)

/** Configurable Home Assistant entity IDs for battery metrics and cell voltages. */
data class HaEntitiesConfig(
    val socEntity: String = DEFAULT_SOC_ENTITY,
    val powerEntity: String = DEFAULT_POWER_ENTITY,
    val fallbackPowerEntity: String = DEFAULT_FALLBACK_POWER_ENTITY,
    val currentEntity: String = DEFAULT_CURRENT_ENTITY,
    val totalVoltageEntity: String = DEFAULT_TOTAL_VOLTAGE_ENTITY,
    val deltaVoltageEntity: String = DEFAULT_DELTA_VOLTAGE_ENTITY,
    val minCellEntity: String = DEFAULT_MIN_CELL_ENTITY,
    val maxCellEntity: String = DEFAULT_MAX_CELL_ENTITY,
    val timeStatusEntity: String = DEFAULT_TIME_STATUS_ENTITY,
    val cellEntities: List<String> = DEFAULT_CELL_ENTITIES,
) {
    fun normalized(): HaEntitiesConfig = copy(
        socEntity = socEntity.trim(),
        powerEntity = powerEntity.trim(),
        fallbackPowerEntity = fallbackPowerEntity.trim(),
        currentEntity = currentEntity.trim(),
        totalVoltageEntity = totalVoltageEntity.trim(),
        deltaVoltageEntity = deltaVoltageEntity.trim(),
        minCellEntity = minCellEntity.trim(),
        maxCellEntity = maxCellEntity.trim(),
        timeStatusEntity = timeStatusEntity.trim(),
        cellEntities = cellEntities.map { it.trim() }.filter { it.isNotEmpty() },
    )

    fun toJsonString(): String {
        val cellsArr = JSONArray()
        cellEntities.forEach { cellsArr.put(it) }
        return JSONObject().apply {
            put("soc", socEntity)
            put("power", powerEntity)
            put("fallback_power", fallbackPowerEntity)
            put("current", currentEntity)
            put("total_v", totalVoltageEntity)
            put("delta_v", deltaVoltageEntity)
            put("min_cell", minCellEntity)
            put("max_cell", maxCellEntity)
            put("time_status", timeStatusEntity)
            put("cells", cellsArr)
        }.toString()
    }

    companion object {
        const val DEFAULT_SOC_ENTITY = "sensor.espjkbms_espjkbms_state_of_charge"
        const val DEFAULT_POWER_ENTITY = "sensor.espjkbms_espjkbms_power"
        const val DEFAULT_FALLBACK_POWER_ENTITY = "sensor.aninerel_anl_4200t_24l_w_pro_battery_power"
        const val DEFAULT_CURRENT_ENTITY = "sensor.espjkbms_espjkbms_current"
        const val DEFAULT_TOTAL_VOLTAGE_ENTITY = "sensor.espjkbms_espjkbms_total_voltage"
        const val DEFAULT_DELTA_VOLTAGE_ENTITY = "sensor.espjkbms_espjkbms_delta_cell_voltage"
        const val DEFAULT_MIN_CELL_ENTITY = "sensor.espjkbms_espjkbms_min_voltage_cell"
        const val DEFAULT_MAX_CELL_ENTITY = "sensor.espjkbms_espjkbms_max_voltage_cell"
        const val DEFAULT_TIME_STATUS_ENTITY = "sensor.battery_time_status"
        val DEFAULT_CELL_ENTITIES: List<String> = (1..8).map {
            "sensor.espjkbms_espjkbms_cell_voltage_$it"
        }

        private val ENTITY_ID_REGEX = Regex("^[a-zA-Z0-9_]+\\.[a-zA-Z0-9_]+$")

        fun isValidEntityId(raw: String): Boolean = ENTITY_ID_REGEX.matches(raw.trim())

        fun validate(config: HaEntitiesConfig): String? {
            val norm = config.normalized()
            if (norm.socEntity.isBlank()) {
                return "Помилка: вкажіть сутність заряду (%)"
            }
            val checks = buildList {
                add("Заряд (%)" to norm.socEntity)
                if (norm.powerEntity.isNotBlank()) add("Потужність" to norm.powerEntity)
                if (norm.fallbackPowerEntity.isNotBlank()) add("Резервна потужність" to norm.fallbackPowerEntity)
                if (norm.currentEntity.isNotBlank()) add("Струм" to norm.currentEntity)
                if (norm.totalVoltageEntity.isNotBlank()) add("Загальна напруга" to norm.totalVoltageEntity)
                if (norm.deltaVoltageEntity.isNotBlank()) add("Дельта комірок" to norm.deltaVoltageEntity)
                if (norm.minCellEntity.isNotBlank()) add("Мін. комірка" to norm.minCellEntity)
                if (norm.maxCellEntity.isNotBlank()) add("Макс. комірка" to norm.maxCellEntity)
                if (norm.timeStatusEntity.isNotBlank()) add("Статус часу" to norm.timeStatusEntity)
                norm.cellEntities.forEachIndexed { index, entity ->
                    add("Комірка ${index + 1}" to entity)
                }
            }
            for ((label, entity) in checks) {
                if (!isValidEntityId(entity)) {
                    return "Помилка: некоректний entity_id для «$label» ($entity)"
                }
            }
            return null
        }

        fun fromJsonString(raw: String): HaEntitiesConfig? = try {
            val obj = JSONObject(raw)
            fun readField(key: String, default: String): String =
                if (obj.has(key)) obj.optString(key, "") else default

            val cells = if (obj.has("cells")) {
                val arr = obj.optJSONArray("cells") ?: JSONArray()
                buildList {
                    for (i in 0 until arr.length()) {
                        val value = arr.optString(i, "").trim()
                        if (value.isNotEmpty()) add(value)
                    }
                }
            } else {
                DEFAULT_CELL_ENTITIES
            }
            HaEntitiesConfig(
                socEntity = readField("soc", DEFAULT_SOC_ENTITY),
                powerEntity = readField("power", DEFAULT_POWER_ENTITY),
                fallbackPowerEntity = readField("fallback_power", DEFAULT_FALLBACK_POWER_ENTITY),
                currentEntity = readField("current", DEFAULT_CURRENT_ENTITY),
                totalVoltageEntity = readField("total_v", DEFAULT_TOTAL_VOLTAGE_ENTITY),
                deltaVoltageEntity = readField("delta_v", DEFAULT_DELTA_VOLTAGE_ENTITY),
                minCellEntity = readField("min_cell", DEFAULT_MIN_CELL_ENTITY),
                maxCellEntity = readField("max_cell", DEFAULT_MAX_CELL_ENTITY),
                timeStatusEntity = readField("time_status", DEFAULT_TIME_STATUS_ENTITY),
                cellEntities = cells,
            )
        } catch (_: Exception) {
            null
        }
    }
}

/** Persists Home Assistant connection settings, widget refresh settings, and cached data. */
class SettingsStore(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun loadConfig(): HaConnectionConfig = HaConnectionConfig(
        primaryUrl = prefs.getString(KEY_PRIMARY_URL, DEFAULT_PRIMARY_URL).orEmpty().ifBlank { DEFAULT_PRIMARY_URL },
        fallbackUrl = prefs.getString(KEY_FALLBACK_URL, DEFAULT_FALLBACK_URL).orEmpty(),
        accessToken = prefs.getString(KEY_ACCESS_TOKEN, DEFAULT_TOKEN).orEmpty().ifBlank { DEFAULT_TOKEN },
    )

    fun saveConfig(primaryUrl: String, fallbackUrl: String, accessToken: String) {
        prefs.edit()
            .putString(KEY_PRIMARY_URL, primaryUrl.trim().trimEnd('/'))
            .putString(KEY_FALLBACK_URL, fallbackUrl.trim().trimEnd('/'))
            .putString(KEY_ACCESS_TOKEN, accessToken.trim())
            .apply()
    }

    fun loadRefreshConfig(): WidgetRefreshConfig = WidgetRefreshConfig(
        autoRefreshEnabled = prefs.getBoolean(KEY_AUTO_REFRESH, true),
        intervalMinutes = prefs.getInt(
            KEY_REFRESH_INTERVAL_MIN,
            WidgetRefreshConfig.DEFAULT_INTERVAL_MINUTES,
        ).coerceIn(
            WidgetRefreshConfig.MIN_INTERVAL_MINUTES,
            WidgetRefreshConfig.MAX_INTERVAL_MINUTES,
        ),
    )

    fun saveRefreshConfig(autoRefreshEnabled: Boolean, intervalMinutes: Int): WidgetRefreshConfig {
        val sanitized = intervalMinutes.coerceIn(
            WidgetRefreshConfig.MIN_INTERVAL_MINUTES,
            WidgetRefreshConfig.MAX_INTERVAL_MINUTES,
        )
        prefs.edit()
            .putBoolean(KEY_AUTO_REFRESH, autoRefreshEnabled)
            .putInt(KEY_REFRESH_INTERVAL_MIN, sanitized)
            .apply()
        return WidgetRefreshConfig(autoRefreshEnabled, sanitized)
    }

    fun loadCachedSnapshot(): BatterySnapshot? {
        val raw = prefs.getString(KEY_CACHED_SNAPSHOT, null) ?: return null
        return BatterySnapshot.fromJsonString(raw)
    }

    fun saveCachedSnapshot(snapshot: BatterySnapshot) {
        prefs.edit().putString(KEY_CACHED_SNAPSHOT, snapshot.toJsonString()).apply()
    }

    fun loadNotificationConfig(): BatteryNotificationConfig = BatteryNotificationConfig(
        notifyOnFullEnabled = prefs.getBoolean(KEY_NOTIFY_ON_FULL, false),
        fullChargeNotified = prefs.getBoolean(KEY_FULL_CHARGE_NOTIFIED, false),
    )

    fun setNotifyOnFullEnabled(enabled: Boolean): BatteryNotificationConfig {
        val editor = prefs.edit().putBoolean(KEY_NOTIFY_ON_FULL, enabled)
        if (!enabled) {
            editor.putBoolean(KEY_FULL_CHARGE_NOTIFIED, false)
        }
        editor.apply()
        return loadNotificationConfig()
    }

    fun setFullChargeNotified(notified: Boolean) {
        prefs.edit().putBoolean(KEY_FULL_CHARGE_NOTIFIED, notified).apply()
    }

    fun loadEntitiesConfig(): HaEntitiesConfig {
        val raw = prefs.getString(KEY_ENTITIES_JSON, null) ?: return HaEntitiesConfig()
        return HaEntitiesConfig.fromJsonString(raw) ?: HaEntitiesConfig()
    }

    fun saveEntitiesConfig(config: HaEntitiesConfig): HaEntitiesConfig {
        val normalized = config.normalized()
        prefs.edit().putString(KEY_ENTITIES_JSON, normalized.toJsonString()).apply()
        return normalized
    }

    companion object {
        private const val PREFS_NAME = "ha_battery_widget_prefs"
        private const val KEY_PRIMARY_URL = "primary_url"
        private const val KEY_FALLBACK_URL = "fallback_url"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_CACHED_SNAPSHOT = "cached_snapshot"
        private const val KEY_AUTO_REFRESH = "auto_refresh_enabled"
        private const val KEY_REFRESH_INTERVAL_MIN = "refresh_interval_minutes"
        private const val KEY_NOTIFY_ON_FULL = "notify_on_full_enabled"
        private const val KEY_FULL_CHARGE_NOTIFIED = "full_charge_notified"
        private const val KEY_ENTITIES_JSON = "ha_entities_json"

        const val DEFAULT_PRIMARY_URL = "http://homeassistant.local:8123"
        const val DEFAULT_FALLBACK_URL = ""
        const val DEFAULT_TOKEN = ""
    }
}
