package com.homeassistant.batterywidget.ui.main

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsToggleable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import com.homeassistant.batterywidget.data.BatterySnapshot
import org.junit.Rule
import org.junit.Test

/** UI tests for [com.homeassistant.batterywidget.ui.main.MainScreenContent]. */
class MainScreenTest {

    @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun batteryPercentage_isDisplayed() {
        composeTestRule.setContent {
            MainScreenContent(
                state = MainUiState(
                    snapshot = sampleSnapshot(),
                ),
                onRefresh = {},
                onAutoRefreshChange = {},
                onIntervalInputChange = {},
                onSaveRefreshSettings = {},
                onPrimaryUrlChange = {},
                onFallbackUrlChange = {},
                onTokenChange = {},
                onSaveSettings = {},
                onPinWidget = {},
            )
        }
        composeTestRule.onNodeWithText("87%").assertExists()
    }

    @Test
    fun fullChargeNotificationCheckbox_togglesOnAndOff() {
        composeTestRule.setContent {
            var state by remember {
                mutableStateOf(MainUiState(snapshot = sampleSnapshot()))
            }
            MainScreenContent(
                state = state,
                onRefresh = {},
                onAutoRefreshChange = {},
                onIntervalInputChange = {},
                onSaveRefreshSettings = {},
                onNotifyOnFullChange = { enabled ->
                    state = state.copy(notifyOnFullEnabled = enabled)
                },
                onPrimaryUrlChange = {},
                onFallbackUrlChange = {},
                onTokenChange = {},
                onSaveSettings = {},
                onPinWidget = {},
            )
        }

        val checkbox = composeTestRule.onNodeWithTag("notify_full_checkbox")
        checkbox.performScrollTo()
        checkbox.assertIsToggleable()
        checkbox.assertIsOff()

        checkbox.performClick()
        checkbox.assertIsOn()

        checkbox.performClick()
        checkbox.assertIsOff()
    }

    @Test
    fun entitiesSettings_expandsAndUpdatesEntityFields() {
        var saved = false
        composeTestRule.setContent {
            var state by remember {
                mutableStateOf(MainUiState(snapshot = sampleSnapshot()))
            }
            MainScreenContent(
                state = state,
                onRefresh = {},
                onAutoRefreshChange = {},
                onIntervalInputChange = {},
                onSaveRefreshSettings = {},
                onPrimaryUrlChange = {},
                onFallbackUrlChange = {},
                onTokenChange = {},
                onSaveSettings = {},
                entitiesCallbacks = HaEntitiesCallbacks(
                    onSocEntityChange = { value -> state = state.copy(socEntity = value) },
                    onSaveEntities = { saved = true },
                ),
                onPinWidget = {},
            )
        }

        val toggleBtn = composeTestRule.onNodeWithTag("toggle_entities_settings")
        toggleBtn.performScrollTo()
        toggleBtn.performClick()

        val socField = composeTestRule.onNodeWithTag("entity_soc")
        socField.performScrollTo()
        socField.performTextReplacement("sensor.custom_soc")

        val saveBtn = composeTestRule.onNodeWithTag("save_entities_button")
        saveBtn.performScrollTo()
        saveBtn.performClick()

        assert(saved)
    }

    private fun sampleSnapshot() = BatterySnapshot(
        socPercent = 87.0,
        powerWatts = -226.0,
        currentAmps = -8.5,
        totalVoltage = 26.54,
        deltaVolts = 0.001,
        minCellIndex = 1,
        maxCellIndex = 5,
        timeStatus = "🔴 До 0%: 31г 54хв | 226W",
        cellVoltages = listOf(3.317, 3.317, 3.317, 3.317, 3.318, 3.317, 3.317, 3.317),
        updatedAtMillis = 1794300000000L,
    )
}
