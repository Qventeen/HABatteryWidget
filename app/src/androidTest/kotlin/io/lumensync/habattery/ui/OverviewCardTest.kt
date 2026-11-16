package io.lumensync.habattery.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
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
import io.lumensync.habattery.domain.model.FontPercents
import io.lumensync.habattery.domain.model.Settings
import io.lumensync.habattery.domain.model.ThemeChoice
import io.lumensync.habattery.format.Formatters
import io.lumensync.habattery.ui.sections.OverviewCard
import io.lumensync.habattery.ui.theme.HaBatteryTheme
import io.lumensync.habattery.widget.WidgetPalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
        OverviewCard(model(sample), FontPercents(), ThemeChoice.SYSTEM, false, { refreshes++ }, { toggles++ })
      }
    }
    compose.onNodeWithText("55").assertExists()
    compose.onNodeWithText(Formatters(context).voltage(26.5)).assertExists()
    compose
      .onNodeWithContentDescription(context.resources.getQuantityString(R.plurals.widget_soc_description, 55, 55))
      .assertExists()
    compose.onNodeWithContentDescription(context.getString(R.string.widget_auto_off)).performClick()
    compose.onNodeWithContentDescription(context.getString(R.string.widget_refresh_action)).performClick()
    assertEquals(1, toggles)
    assertEquals(1, refreshes)
  }

  /** The chosen theme wins over the device's mode: Dark draws the dark card on any device. */
  @Test
  fun darkTheme_drawsTheDarkCard() {
    compose.setContent {
      HaBatteryTheme(darkTheme = true) {
        OverviewCard(model(sample), FontPercents(), ThemeChoice.DARK, false, {}, {}, Modifier.testTag("card"))
      }
    }
    compose.waitForIdle()
    val pixels = compose.onNodeWithTag("card").captureToImage().toPixelMap()
    // Bottom middle of the card: its padding, no content on top.
    val pixel = pixels[pixels.width / 2, pixels.height - 6]
    assertTrue("$pixel", pixel.isCloseTo(WidgetPalette.Dark.card))
  }

  /** The message without data follows the system text size, like the rest of the screen. */
  @Test
  fun emptyMessage_followsLargeSystemText() {
    compose.setContent {
      val density = LocalDensity.current
      HaBatteryTheme {
        Column {
          listOf(1f to "normal", 2f to "large").forEach { (scale, tag) ->
            CompositionLocalProvider(LocalDensity provides Density(density.density, scale)) {
              OverviewCard(model(null, hasToken = false), FontPercents(), ThemeChoice.SYSTEM, false, {}, {}, Modifier.testTag(tag))
            }
          }
        }
      }
    }
    fun textHeight(tag: String) =
      compose
        .onNode(hasText(context.getString(R.string.overview_setup)) and hasAnyAncestor(hasTestTag(tag)))
        .getBoundsInRoot()
        .let { it.bottom - it.top }
    assertTrue(textHeight("large") > textHeight("normal"))
  }

  @Test
  fun setup_showsTheSetupText() {
    compose.setContent {
      HaBatteryTheme { OverviewCard(model(null, hasToken = false), FontPercents(), ThemeChoice.SYSTEM, false, {}, {}) }
    }
    compose.onNodeWithText(context.getString(R.string.overview_setup)).assertExists()
  }
}
