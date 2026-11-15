package io.lumensync.habattery.ui

import android.os.Build
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.core.app.ApplicationProvider
import androidx.test.filters.SdkSuppress
import io.lumensync.habattery.App
import io.lumensync.habattery.MainActivity
import io.lumensync.habattery.R
import io.lumensync.habattery.container
import io.lumensync.habattery.domain.model.CacheState
import io.lumensync.habattery.domain.model.LanguageChoice
import io.lumensync.habattery.domain.model.Settings
import io.lumensync.habattery.ui.sections.LANGUAGE_ROW_TAG
import io.lumensync.habattery.ui.sections.CONNECTION_SECTION_TAG
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Smoke test of the real screen. It needs the empty state of a fresh install, so a rule clears the
 * saved settings, token and reading through the app's own stores before the activity starts (an
 * earlier manual run on the same emulator may have left them).
 */
@RunWith(AndroidJUnit4::class)
class MainScreenTest {
  private val freshState =
    object : ExternalResource() {
      override fun before() {
        val container = ApplicationProvider.getApplicationContext<App>().container
        runBlocking {
          container.settingsRepository.update { Settings() }
          container.tokenStore.clear()
          container.readingCache.update { CacheState() }
        }
      }
    }
  private val activity = createAndroidComposeRule<MainActivity>()

  @get:Rule val chain: RuleChain = RuleChain.outerRule(freshState).around(activity)
  private val compose get() = activity

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

  /** Brightness of a pixel at the left edge, half way down: the screen's background. */
  private fun backgroundLuminance(): Float {
    val pixels = compose.onRoot().captureToImage().toPixelMap()
    return pixels[2, pixels.height / 2].luminance()
  }

  @Test
  fun themeSwitch_changesTheAppColours() {
    waitForContent()
    compose.onNodeWithText(text(R.string.appearance_title)).performScrollTo().performClick()

    compose.onNodeWithText(text(R.string.appearance_theme_dark)).performScrollTo().performClick()
    compose.waitForIdle()
    assertTrue(backgroundLuminance() < 0.3f)

    compose.onNodeWithText(text(R.string.appearance_theme_light)).performScrollTo().performClick()
    compose.waitForIdle()
    assertTrue(backgroundLuminance() > 0.7f)

    // Leave the stored choice as a fresh install has it.
    // The theme row comes first; the language row (Android 13+) has a System button too.
    compose.onAllNodesWithText(text(R.string.appearance_system))[0].performScrollTo().performClick()
  }

  @Test
  fun languageRow_existsOnlyFromAndroid13() {
    waitForContent()
    compose.onNodeWithText(text(R.string.appearance_title)).performScrollTo().performClick()
    compose.waitForIdle()

    val row = compose.onNodeWithTag(LANGUAGE_ROW_TAG)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      row.performScrollTo().assertIsDisplayed()
    } else {
      row.assertDoesNotExist()
    }
  }

  @Test
  @SdkSuppress(minSdkVersion = Build.VERSION_CODES.TIRAMISU)
  fun languageSwitch_setsTheAppLanguage() {
    waitForContent()
    val language = ApplicationProvider.getApplicationContext<App>().container.appLanguage
    compose.onNodeWithText(text(R.string.appearance_title)).performScrollTo().performClick()
    try {
      compose.onNodeWithText(text(R.string.appearance_language_ukrainian)).performScrollTo().performClick()
      compose.waitUntil(LOAD_TIMEOUT_MS) { language.current() == LanguageChoice.UKRAINIAN }
    } finally {
      // The language lives in the system, not in the saved settings: put it back.
      language.set(LanguageChoice.SYSTEM)
    }
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
