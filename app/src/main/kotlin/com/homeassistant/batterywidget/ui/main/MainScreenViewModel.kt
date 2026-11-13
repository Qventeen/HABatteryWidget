package com.homeassistant.batterywidget.ui.main

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homeassistant.batterywidget.data.BatterySnapshot
import com.homeassistant.batterywidget.data.HaConnectionConfig
import com.homeassistant.batterywidget.data.HaEntitiesConfig
import com.homeassistant.batterywidget.data.HomeAssistantClient
import com.homeassistant.batterywidget.data.SettingsStore
import com.homeassistant.batterywidget.data.WidgetRefreshConfig
import com.homeassistant.batterywidget.notifications.BatteryFullNotificationManager
import com.homeassistant.batterywidget.widget.WidgetSyncWorker
import com.homeassistant.batterywidget.widget.pushSnapshotToWidgets
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MainUiState(
    val snapshot: BatterySnapshot? = null,
    val isLoading: Boolean = false,
    val statusMessage: String? = null,
    val primaryUrl: String = SettingsStore.DEFAULT_PRIMARY_URL,
    val fallbackUrl: String = SettingsStore.DEFAULT_FALLBACK_URL,
    val accessToken: String = SettingsStore.DEFAULT_TOKEN,
    val autoRefreshEnabled: Boolean = true,
    val intervalInput: String = WidgetRefreshConfig.DEFAULT_INTERVAL_MINUTES.toString(),
    val notifyOnFullEnabled: Boolean = false,
    val socEntity: String = HaEntitiesConfig.DEFAULT_SOC_ENTITY,
    val powerEntity: String = HaEntitiesConfig.DEFAULT_POWER_ENTITY,
    val fallbackPowerEntity: String = HaEntitiesConfig.DEFAULT_FALLBACK_POWER_ENTITY,
    val currentEntity: String = HaEntitiesConfig.DEFAULT_CURRENT_ENTITY,
    val totalVoltageEntity: String = HaEntitiesConfig.DEFAULT_TOTAL_VOLTAGE_ENTITY,
    val deltaVoltageEntity: String = HaEntitiesConfig.DEFAULT_DELTA_VOLTAGE_ENTITY,
    val minCellEntity: String = HaEntitiesConfig.DEFAULT_MIN_CELL_ENTITY,
    val maxCellEntity: String = HaEntitiesConfig.DEFAULT_MAX_CELL_ENTITY,
    val timeStatusEntity: String = HaEntitiesConfig.DEFAULT_TIME_STATUS_ENTITY,
    val cellEntities: List<String> = HaEntitiesConfig.DEFAULT_CELL_ENTITIES,
) {
    fun toEntitiesConfig(): HaEntitiesConfig = HaEntitiesConfig(
        socEntity = socEntity,
        powerEntity = powerEntity,
        fallbackPowerEntity = fallbackPowerEntity,
        currentEntity = currentEntity,
        totalVoltageEntity = totalVoltageEntity,
        deltaVoltageEntity = deltaVoltageEntity,
        minCellEntity = minCellEntity,
        maxCellEntity = maxCellEntity,
        timeStatusEntity = timeStatusEntity,
        cellEntities = cellEntities,
    )
}

class MainScreenViewModel(
    private val settingsStore: SettingsStore? = null,
    private val fetcher: suspend (HaConnectionConfig, HaEntitiesConfig) -> Result<BatterySnapshot> = HomeAssistantClient::fetchBatterySnapshot,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        settingsStore?.let { store ->
            val cfg = store.loadConfig()
            val refreshCfg = store.loadRefreshConfig()
            val notificationCfg = store.loadNotificationConfig()
            val entitiesCfg = store.loadEntitiesConfig()
            MainUiState(
                snapshot = store.loadCachedSnapshot(),
                primaryUrl = cfg.primaryUrl,
                fallbackUrl = cfg.fallbackUrl,
                accessToken = cfg.accessToken,
                autoRefreshEnabled = refreshCfg.autoRefreshEnabled,
                intervalInput = refreshCfg.sanitizedIntervalMinutes.toString(),
                notifyOnFullEnabled = notificationCfg.notifyOnFullEnabled,
                socEntity = entitiesCfg.socEntity,
                powerEntity = entitiesCfg.powerEntity,
                fallbackPowerEntity = entitiesCfg.fallbackPowerEntity,
                currentEntity = entitiesCfg.currentEntity,
                totalVoltageEntity = entitiesCfg.totalVoltageEntity,
                deltaVoltageEntity = entitiesCfg.deltaVoltageEntity,
                minCellEntity = entitiesCfg.minCellEntity,
                maxCellEntity = entitiesCfg.maxCellEntity,
                timeStatusEntity = entitiesCfg.timeStatusEntity,
                cellEntities = entitiesCfg.cellEntities,
            )
        } ?: MainUiState()
    )
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    fun updatePrimaryUrl(value: String) {
        _uiState.update { it.copy(primaryUrl = value) }
    }

    fun updateFallbackUrl(value: String) {
        _uiState.update { it.copy(fallbackUrl = value) }
    }

    fun updateAccessToken(value: String) {
        _uiState.update { it.copy(accessToken = value) }
    }

    fun updateSocEntity(value: String) {
        _uiState.update { it.copy(socEntity = value) }
    }

    fun updatePowerEntity(value: String) {
        _uiState.update { it.copy(powerEntity = value) }
    }

    fun updateFallbackPowerEntity(value: String) {
        _uiState.update { it.copy(fallbackPowerEntity = value) }
    }

    fun updateCurrentEntity(value: String) {
        _uiState.update { it.copy(currentEntity = value) }
    }

    fun updateTotalVoltageEntity(value: String) {
        _uiState.update { it.copy(totalVoltageEntity = value) }
    }

    fun updateDeltaVoltageEntity(value: String) {
        _uiState.update { it.copy(deltaVoltageEntity = value) }
    }

    fun updateMinCellEntity(value: String) {
        _uiState.update { it.copy(minCellEntity = value) }
    }

    fun updateMaxCellEntity(value: String) {
        _uiState.update { it.copy(maxCellEntity = value) }
    }

    fun updateTimeStatusEntity(value: String) {
        _uiState.update { it.copy(timeStatusEntity = value) }
    }

    fun updateCellEntity(index: Int, value: String) {
        _uiState.update { state ->
            if (index !in state.cellEntities.indices) return@update state
            val updated = state.cellEntities.toMutableList().apply { set(index, value) }
            state.copy(cellEntities = updated)
        }
    }

    fun addCellEntity() {
        _uiState.update { state ->
            if (state.cellEntities.size >= MAX_CELL_ENTITIES) return@update state
            state.copy(cellEntities = state.cellEntities + "")
        }
    }

    fun removeCellEntity(index: Int) {
        _uiState.update { state ->
            if (index !in state.cellEntities.indices) return@update state
            val updated = state.cellEntities.toMutableList().apply { removeAt(index) }
            state.copy(cellEntities = updated)
        }
    }

    fun setAutoRefresh(enabled: Boolean) {
        _uiState.update { it.copy(autoRefreshEnabled = enabled) }
    }

    fun updateIntervalInput(raw: String) {
        val digitsOnly = raw.filter { it.isDigit() }.take(4)
        _uiState.update { it.copy(intervalInput = digitsOnly) }
    }

    fun setNotifyOnFull(enabled: Boolean) {
        val saved = settingsStore?.setNotifyOnFullEnabled(enabled)
        val actualEnabled = saved?.notifyOnFullEnabled ?: enabled
        _uiState.update {
            it.copy(
                notifyOnFullEnabled = actualEnabled,
                statusMessage = if (actualEnabled) {
                    "Сповіщення про зарядку до 100% увімкнено"
                } else {
                    "Сповіщення про зарядку до 100% вимкнено"
                },
            )
        }
    }

    fun onNotificationPermissionDenied() {
        settingsStore?.setNotifyOnFullEnabled(false)
        _uiState.update {
            it.copy(
                notifyOnFullEnabled = false,
                statusMessage = "Помилка: надайте дозвіл на сповіщення для увімкнення",
            )
        }
    }

    fun refresh(appContext: Context? = null) {
        val current = _uiState.value
        val config = HaConnectionConfig(
            primaryUrl = current.primaryUrl.trim().trimEnd('/'),
            fallbackUrl = current.fallbackUrl.trim().trimEnd('/'),
            accessToken = current.accessToken.trim(),
        )
        val entities = settingsStore?.loadEntitiesConfig() ?: current.toEntitiesConfig().normalized()

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, statusMessage = null) }
            val result = fetcher(config, entities)
            result.fold(
                onSuccess = { fresh ->
                    settingsStore?.saveCachedSnapshot(fresh)
                    if (appContext != null) {
                        BatteryFullNotificationManager.handleFreshSnapshot(appContext, fresh)
                        pushSnapshotToWidgets(appContext, fresh, isRefreshing = false)
                    }
                    _uiState.update {
                        it.copy(
                            snapshot = fresh,
                            isLoading = false,
                            statusMessage = "Дані оновлено",
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            statusMessage = "Помилка: ${err.message ?: "немає зв'язку"}",
                        )
                    }
                },
            )
        }
    }

    fun saveRefreshSettings(appContext: Context? = null) {
        val current = _uiState.value
        val parsed = current.intervalInput.toIntOrNull()
        if (current.autoRefreshEnabled && (parsed == null || parsed !in WidgetRefreshConfig.MIN_INTERVAL_MINUTES..WidgetRefreshConfig.MAX_INTERVAL_MINUTES)) {
            _uiState.update {
                it.copy(statusMessage = "Помилка: вкажіть частоту від 1 до 1440 хв")
            }
            return
        }

        val minutes = (parsed ?: WidgetRefreshConfig.DEFAULT_INTERVAL_MINUTES)
            .coerceIn(WidgetRefreshConfig.MIN_INTERVAL_MINUTES, WidgetRefreshConfig.MAX_INTERVAL_MINUTES)
        val saved = settingsStore?.saveRefreshConfig(current.autoRefreshEnabled, minutes)
            ?: WidgetRefreshConfig(current.autoRefreshEnabled, minutes)

        _uiState.update {
            it.copy(
                autoRefreshEnabled = saved.autoRefreshEnabled,
                intervalInput = saved.sanitizedIntervalMinutes.toString(),
                statusMessage = if (saved.autoRefreshEnabled) {
                    "Збережено: автооновлення кожні ${saved.sanitizedIntervalMinutes} хв"
                } else {
                    "Збережено: лише ручне оновлення"
                },
            )
        }

        if (appContext != null) {
            WidgetSyncWorker.applyRefreshSchedule(appContext, forceReschedule = true)
            viewModelScope.launch {
                pushSnapshotToWidgets(appContext, _uiState.value.snapshot, isRefreshing = false)
            }
        }
    }

    fun saveSettingsAndRefresh(appContext: Context) {
        val current = _uiState.value
        settingsStore?.saveConfig(
            primaryUrl = current.primaryUrl,
            fallbackUrl = current.fallbackUrl,
            accessToken = current.accessToken,
        )
        WidgetSyncWorker.applyRefreshSchedule(appContext, forceReschedule = false)
        refresh(appContext)
    }

    fun saveEntitiesAndRefresh(appContext: Context? = null) {
        val current = _uiState.value
        val candidate = current.toEntitiesConfig()
        val error = HaEntitiesConfig.validate(candidate)
        if (error != null) {
            _uiState.update { it.copy(statusMessage = error) }
            return
        }

        val normalized = candidate.normalized()
        settingsStore?.saveEntitiesConfig(normalized)
        _uiState.update {
            it.withEntities(normalized).copy(statusMessage = "Сутності збережено")
        }
        if (appContext != null) {
            refresh(appContext)
        }
    }

    fun resetDefaultEntities(appContext: Context? = null) {
        val defaults = HaEntitiesConfig()
        settingsStore?.saveEntitiesConfig(defaults)
        _uiState.update {
            it.withEntities(defaults).copy(statusMessage = "Повернуто сутності за замовчуванням")
        }
        if (appContext != null) {
            refresh(appContext)
        }
    }

    private fun MainUiState.withEntities(config: HaEntitiesConfig): MainUiState = copy(
        socEntity = config.socEntity,
        powerEntity = config.powerEntity,
        fallbackPowerEntity = config.fallbackPowerEntity,
        currentEntity = config.currentEntity,
        totalVoltageEntity = config.totalVoltageEntity,
        deltaVoltageEntity = config.deltaVoltageEntity,
        minCellEntity = config.minCellEntity,
        maxCellEntity = config.maxCellEntity,
        timeStatusEntity = config.timeStatusEntity,
        cellEntities = config.cellEntities,
    )

    companion object {
        const val MAX_CELL_ENTITIES = 32
    }
}
