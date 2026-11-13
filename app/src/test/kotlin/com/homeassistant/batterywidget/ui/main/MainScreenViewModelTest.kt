package com.homeassistant.batterywidget.ui.main

import com.homeassistant.batterywidget.data.BatteryChargeMode
import com.homeassistant.batterywidget.data.BatteryFormatters
import com.homeassistant.batterywidget.data.BatterySnapshot
import com.homeassistant.batterywidget.data.HaEntitiesConfig
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainScreenViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun formatPower_handlesWattsAndKilowattsBoundaries() {
        assertEquals("0 W", BatteryFormatters.formatPower(0.0))
        assertEquals("226 W", BatteryFormatters.formatPower(-226.4))
        assertEquals("999 W", BatteryFormatters.formatPower(999.4))
        assertEquals("1 kW", BatteryFormatters.formatPower(999.6))
        assertEquals("1 kW", BatteryFormatters.formatPower(1000.0))
        assertEquals("1.5 kW", BatteryFormatters.formatPower(1500.0))
        assertEquals("2.35 kW", BatteryFormatters.formatPower(-2346.0))
    }

    @Test
    fun formatStatusLine_parsesDischargeAndCharge() {
        val discharging = sampleSnapshot(power = -226.0, timeStatus = "🔴 До 0%: 31г 54хв | 226W")
        val disDisplay = BatteryFormatters.formatStatusLine(discharging)
        assertEquals(BatteryChargeMode.DISCHARGING, disDisplay.mode)
        assertEquals("Розряджається 226 W • До 0%: 31г 54хв", disDisplay.text)

        val charging = sampleSnapshot(power = 1450.0, timeStatus = "🟢 До 100%: 1г 10хв | 1450W")
        val chgDisplay = BatteryFormatters.formatStatusLine(charging)
        assertEquals(BatteryChargeMode.CHARGING, chgDisplay.mode)
        assertEquals("Заряджається 1.45 kW • До 100%: 1г 10хв", chgDisplay.text)
    }

    @Test
    fun computeDeltaMv_and_minMaxResolution() {
        val snap = sampleSnapshot(power = -200.0, timeStatus = "🔴 До 0%: 10г | 200W")
        assertEquals(2, BatteryFormatters.computeDeltaMv(snap))
        assertEquals(2 to 5, BatteryFormatters.resolveMinMaxCells(snap))
        assertEquals("26.54 V", BatteryFormatters.formatTotalVoltage(snap.totalVoltage))
        assertEquals("-8.5 A", BatteryFormatters.formatCurrent(snap.currentAmps))
        assertEquals("3.316", BatteryFormatters.formatCellVoltage(snap.cellVoltages[1]))
    }

    @Test
    fun viewModel_refreshUpdatesSnapshot() = runTest(testDispatcher) {
        val expected = sampleSnapshot(power = -1500.0, timeStatus = "🔴 До 0%: 5г 10хв | 1500W")
        val viewModel = MainScreenViewModel(
            settingsStore = null,
            fetcher = { _, _ -> Result.success(expected) },
        )
        viewModel.refresh()
        advanceUntilIdle()
        assertEquals(expected, viewModel.uiState.value.snapshot)
        assertEquals("Дані оновлено", viewModel.uiState.value.statusMessage)
    }

    @Test
    fun viewModel_refreshModeAndCustomIntervalValidation() {
        val viewModel = MainScreenViewModel(settingsStore = null)
        viewModel.updateIntervalInput("5abc")
        assertEquals("5", viewModel.uiState.value.intervalInput)

        viewModel.saveRefreshSettings(null)
        assertEquals("Збережено: автооновлення кожні 5 хв", viewModel.uiState.value.statusMessage)

        viewModel.updateIntervalInput("0")
        viewModel.saveRefreshSettings(null)
        assertEquals("Помилка: вкажіть частоту від 1 до 1440 хв", viewModel.uiState.value.statusMessage)

        viewModel.setAutoRefresh(false)
        viewModel.saveRefreshSettings(null)
        assertEquals("Збережено: лише ручне оновлення", viewModel.uiState.value.statusMessage)
    }

    @Test
    fun viewModel_notifyOnFullToggleAndPermissionDenied() {
        val viewModel = MainScreenViewModel(settingsStore = null)
        assertEquals(false, viewModel.uiState.value.notifyOnFullEnabled)

        viewModel.setNotifyOnFull(true)
        assertEquals(true, viewModel.uiState.value.notifyOnFullEnabled)
        assertEquals("Сповіщення про зарядку до 100% увімкнено", viewModel.uiState.value.statusMessage)

        viewModel.setNotifyOnFull(false)
        assertEquals(false, viewModel.uiState.value.notifyOnFullEnabled)
        assertEquals("Сповіщення про зарядку до 100% вимкнено", viewModel.uiState.value.statusMessage)

        viewModel.setNotifyOnFull(true)
        viewModel.onNotificationPermissionDenied()
        assertEquals(false, viewModel.uiState.value.notifyOnFullEnabled)
        assertEquals(
            "Помилка: надайте дозвіл на сповіщення для увімкнення",
            viewModel.uiState.value.statusMessage,
        )
    }

    @Test
    fun viewModel_entitiesValidationSaveAndReset() = runTest(testDispatcher) {
        var capturedEntities: HaEntitiesConfig? = null
        val expected = sampleSnapshot(power = 500.0, timeStatus = "🟢 До 100%: 2г | 500W")
        val viewModel = MainScreenViewModel(
            settingsStore = null,
            fetcher = { _, entities ->
                capturedEntities = entities
                Result.success(expected)
            },
        )

        // Reject blank SOC entity
        viewModel.updateSocEntity("  ")
        viewModel.saveEntitiesAndRefresh(null)
        assertTrue(viewModel.uiState.value.statusMessage?.startsWith("Помилка:") == true)

        // Reject malformed entity id
        viewModel.updateSocEntity("invalid entity id")
        viewModel.saveEntitiesAndRefresh(null)
        assertTrue(viewModel.uiState.value.statusMessage?.startsWith("Помилка:") == true)

        // Valid custom entities + cell edits
        viewModel.updateSocEntity(" sensor.custom_bms_soc ")
        viewModel.updatePowerEntity("sensor.custom_bms_power")
        viewModel.updateFallbackPowerEntity("")
        viewModel.updateCellEntity(0, "sensor.custom_cell_1")
        viewModel.removeCellEntity(7)
        viewModel.addCellEntity()
        viewModel.updateCellEntity(7, "sensor.custom_cell_8")
        viewModel.saveEntitiesAndRefresh(null)
        assertEquals("Сутності збережено", viewModel.uiState.value.statusMessage)
        assertEquals("sensor.custom_bms_soc", viewModel.uiState.value.socEntity)

        viewModel.refresh(null)
        advanceUntilIdle()
        assertEquals("sensor.custom_bms_soc", capturedEntities?.socEntity)
        assertEquals("sensor.custom_bms_power", capturedEntities?.powerEntity)
        assertEquals("", capturedEntities?.fallbackPowerEntity)
        assertEquals("sensor.custom_cell_1", capturedEntities?.cellEntities?.first())
        assertEquals("sensor.custom_cell_8", capturedEntities?.cellEntities?.last())

        viewModel.resetDefaultEntities(null)
        assertEquals(HaEntitiesConfig.DEFAULT_SOC_ENTITY, viewModel.uiState.value.socEntity)
        assertEquals(HaEntitiesConfig.DEFAULT_CELL_ENTITIES, viewModel.uiState.value.cellEntities)
        assertEquals("Повернуто сутності за замовчуванням", viewModel.uiState.value.statusMessage)
    }

    private fun sampleSnapshot(power: Double, timeStatus: String) = BatterySnapshot(
        socPercent = 87.0,
        powerWatts = power,
        currentAmps = -8.5,
        totalVoltage = 26.54,
        deltaVolts = 0.002,
        minCellIndex = 2,
        maxCellIndex = 5,
        timeStatus = timeStatus,
        cellVoltages = listOf(3.318, 3.316, 3.317, 3.317, 3.318, 3.317, 3.317, 3.317),
        updatedAtMillis = 1794300000000L,
    )
}
