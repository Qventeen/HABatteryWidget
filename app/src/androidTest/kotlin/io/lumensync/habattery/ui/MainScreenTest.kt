package io.lumensync.habattery.ui

import androidx.annotation.StringRes
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.lumensync.habattery.MainActivity
import io.lumensync.habattery.R
import io.lumensync.habattery.ui.sections.CONNECTION_SECTION_TAG
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Smoke test of the real screen. Expects a fresh install (no saved token or entities). */
@RunWith(AndroidJUnit4::class)
class MainScreenTest {
  @get:Rule val compose = createAndroidComposeRule<MainActivity>()

  private fun text(@StringRes id: Int): String = compose.activity.getString(id)

  private fun waitForContent() {
    compose.waitUntil(LOAD_TIMEOUT_MS) {
      compose.onAllNodesWithText(text(R.string.overview_setup)).fetchSemanticsNodes().isNotEmpty()
    }
  }

  @Test
  fun freshInstall_showsSetupTextAndConnection() {
    waitForContent()

    compose.onNodeWithText(text(R.string.overview_setup)).assertIsDisplayed()
    compose.onNodeWithText(text(R.string.connection_title)).performScrollTo().assertIsDisplayed()
  }

  @Test
  fun localUrl_savesWithoutFieldError() {
    waitForContent()

    compose
      .onNodeWithText(text(R.string.connection_url))
      .performScrollTo()
      .performTextReplacement("http://10.0.2.2:8123")
    saveConnection()

    listOf(R.string.field_required, R.string.field_url_invalid).forEach {
      compose.onNodeWithText(text(it)).assertDoesNotExist()
    }
  }

  @Test
  fun save_clearsFocus() {
    waitForContent()
    val field = compose.onNodeWithText(text(R.string.connection_url))
    field.performScrollTo().performClick().performTextReplacement("http://10.0.2.2:8123")
    field.assertIsFocused()

    saveConnection()

    // No focused field: the keyboard does not open again when the user comes back.
    field.assertIsNotFocused()
  }

  @Test
  fun fillSetsEveryEntityFieldFromPrefix() {
    waitForContent()

    compose
      .onNodeWithText(text(R.string.entities_prefix))
      .performScrollTo()
      .performTextReplacement("bps_bms")
    compose.onNodeWithText(text(R.string.entities_fill)).performScrollTo().performClick()
    compose.waitForIdle()

    compose.onNodeWithText("sensor.bps_bms_power_tube_temperature").performScrollTo().assertIsDisplayed()
    compose.onNodeWithText("sensor.bps_bms_temperature_sensor_1").performScrollTo().assertIsDisplayed()
  }

  @Test
  fun sectionsFoldAndUnfold() {
    waitForContent()
    val label = text(R.string.capacity_label)
    val header = compose.onNodeWithText(text(R.string.capacity_title))

    // The capacity section starts folded: its content is not composed.
    compose.onNodeWithText(label).assertDoesNotExist()

    header.performScrollTo().performClick()
    compose.onNodeWithText(label).assertExists()

    header.performClick()
    compose.onNodeWithText(label).assertDoesNotExist()
  }

  @Test
  fun widgetSection_choosesAh() {
    waitForContent()
    compose.onNodeWithText(text(R.string.widget_settings_title)).performScrollTo().performClick()

    compose.onNodeWithText(text(R.string.widget_choice_ah)).performScrollTo().performClick()
    compose.waitForIdle()

    val summary =
      compose.activity.getString(
        R.string.widget_summary,
        text(R.string.widget_choice_ah),
        text(R.string.widget_choice_current),
      )
    compose.onNodeWithText(summary).assertExists()
    // Leave the stored choice as a fresh install has it.
    compose.onNodeWithText(text(R.string.widget_choice_kwh)).performScrollTo().performClick()
  }

  private fun saveConnection() {
    compose
      .onNode(hasText(text(R.string.action_save)) and hasAnyAncestor(hasTestTag(CONNECTION_SECTION_TAG)))
      .performScrollTo()
      .performClick()
    compose.waitForIdle()
  }

  private companion object {
    const val LOAD_TIMEOUT_MS = 5_000L
  }
}
