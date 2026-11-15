package io.lumensync.habattery.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.lumensync.habattery.R
import io.lumensync.habattery.domain.BatteryOverview
import io.lumensync.habattery.domain.WidgetModel
import io.lumensync.habattery.domain.model.BatteryReading
import io.lumensync.habattery.domain.model.CacheState
import io.lumensync.habattery.domain.model.ConnectionSettings
import io.lumensync.habattery.domain.model.EntitySettings
import io.lumensync.habattery.domain.model.FontSizes
import io.lumensync.habattery.domain.model.Settings
import io.lumensync.habattery.format.Formatters
import io.lumensync.habattery.ui.sections.OverviewCard
import io.lumensync.habattery.ui.theme.HaBatteryTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The app card draws the widget model: values, descriptions, and the two buttons. */
@RunWith(AndroidJUnit4::class)
class OverviewCardTest {
  @get:Rule val compose = createComposeRule()

  private val context = InstrumentationRegistry.getInstrumentation().targetContext

  private val settings =
    Settings(
      connection = ConnectionSettings("http://10.0.2.2:8123"),
      entities = EntitySettings(charge = "sensor.soc", temperatures = listOf("sensor.t1")),
    )

  private fun model(reading: BatteryReading?, hasToken: Boolean = true): WidgetModel =
    WidgetModel.build(BatteryOverview.build(CacheState(reading), settings, hasToken, 2_000L), settings)

  private val sample =
    BatteryReading(
      chargePercent = 54.6,
      currentA = -10.0,
      totalVoltageV = 26.5,
      capacityRemainingAh = 157.0,
      temperaturesC = listOf(25.0),
      updatedAtMillis = 1_000L,
    )

  @Test
  fun data_showsValuesAndTogglesAuto() {
    var toggles = 0
    var refreshes = 0
    compose.setContent {
      HaBatteryTheme {
        OverviewCard(model(sample), FontSizes(), false, { refreshes++ }, { toggles++ })
      }
    }
    compose.onNodeWithText("55").assertExists()
    compose.onNodeWithText(Formatters(context).voltage(26.5)).assertExists()
    compose
      .onNodeWithContentDescription(context.getString(R.string.widget_soc_description, 55))
      .assertExists()
    compose.onNodeWithContentDescription(context.getString(R.string.widget_auto_off)).performClick()
    compose.onNodeWithContentDescription(context.getString(R.string.widget_refresh_action)).performClick()
    assertEquals(1, toggles)
    assertEquals(1, refreshes)
  }

  @Test
  fun setup_showsTheSetupText() {
    compose.setContent {
      HaBatteryTheme { OverviewCard(model(null, hasToken = false), FontSizes(), false, {}, {}) }
    }
    compose.onNodeWithText(context.getString(R.string.overview_setup)).assertExists()
  }
}
