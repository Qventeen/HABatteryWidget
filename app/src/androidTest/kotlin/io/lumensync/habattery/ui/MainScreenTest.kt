package io.lumensync.habattery.ui

import android.os.Build
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.SemanticsNodeInteraction
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
import io.lumensync.habattery.domain.model.FontPercents
import io.lumensync.habattery.domain.model.HomeScale
import io.lumensync.habattery.domain.model.LanguageChoice
import io.lumensync.habattery.domain.model.Settings
import io.lumensync.habattery.domain.model.TokenType
import io.lumensync.habattery.ui.sections.LANGUAGE_ROW_TAG
import io.lumensync.habattery.ui.sections.STRIP_PREVIEW_TAG
import io.lumensync.habattery.ui.sections.WIDGET_PREVIEW_TAG
import io.lumensync.habattery.widget.WidgetPalette
import io.lumensync.habattery.ui.sections.CONNECTION_SECTION_TAG
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
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
          TokenType.entries.forEach { container.tokenStore.clear(it) }
          container.readingCache.update { CacheState() }
        }
      }
    }
  private val activity = createAndroidComposeRule<MainActivity>()

  @get:Rule val chain: RuleChain = RuleChain.outerRule(freshState).around(activity)
  private val compose get() = activity

  private fun text(@StringRes id: Int): String = compose.activity.getString(id)

  /** The bottom bar comes with the first state from storage. */
  private fun waitForContent() {
    compose.waitUntil(LOAD_TIMEOUT_MS) {
      compose.onAllNodesWithTag(appTabTag(AppTab.COMMON)).fetchSemanticsNodes().isNotEmpty()
    }
  }

  private fun openTab(tab: AppTab) {
    compose.onNodeWithTag(appTabTag(tab)).performClick()
    compose.waitForIdle()
  }

  /** No URL and no token: the app opens on Common; the setup text stays under the battery preview. */
  @Test
  fun freshInstall_opensCommon_setupTextOnBattery() {
    waitForContent()

    compose.onNodeWithTag(appTabTag(AppTab.COMMON)).assertIsSelected()
    compose.onNodeWithText(text(R.string.connection_title)).performScrollTo().assertIsDisplayed()
    openTab(AppTab.BATTERY)
    compose.onNodeWithText(text(R.string.overview_setup)).assertIsDisplayed()
  }

  @Test
  fun refreshNow_inTheTopBarOnEveryTab() {
    waitForContent()

    AppTab.entries.forEach {
      openTab(it)
      compose.onNodeWithContentDescription(text(R.string.action_refresh_now)).assertIsDisplayed()
    }
  }

  @Test
  fun localUrl_savesWithoutFieldError() {
    waitForContent()
    openTab(AppTab.COMMON)

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
    openTab(AppTab.COMMON)
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
    openTab(AppTab.BATTERY)

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
    openTab(AppTab.BATTERY)
    val label = text(R.string.capacity_label)
    val header = compose.onNodeWithText(text(R.string.pack_title))

    // The Pack section starts folded: its content is not composed.
    compose.onNodeWithText(label).assertDoesNotExist()

    header.performScrollTo().performClick()
    compose.onNodeWithText(label).assertExists()

    header.performClick()
    compose.onNodeWithText(label).assertDoesNotExist()
  }

  @Test
  fun widgetSection_choosesAh() {
    waitForContent()
    openTab(AppTab.BATTERY)
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

  private fun top(node: SemanticsNodeInteraction) =
    node.fetchSemanticsNode().positionInRoot.y

  /**
   * Battery: preview, "Add to home screen" (when the launcher can pin), Widget, Levels and alerts,
   * Entities, Pack.
   */
  @Test
  fun batteryTab_order() {
    waitForContent()
    openTab(AppTab.BATTERY)
    val preview = top(compose.onNodeWithTag(WIDGET_PREVIEW_TAG))
    val add =
      compose.onAllNodesWithText(text(R.string.action_add_widget)).fetchSemanticsNodes().firstOrNull()
    val widget = top(compose.onNodeWithText(text(R.string.widget_settings_title)))
    val colors = top(compose.onNodeWithText(text(R.string.colors_title)))
    val entities = top(compose.onNodeWithText(text(R.string.entities_title)))
    val pack = top(compose.onNodeWithText(text(R.string.pack_title)))
    add?.positionInRoot?.y?.let { assertTrue(preview < it && it < widget) }
    assertTrue(preview < widget && widget < colors && colors < entities && entities < pack)
    compose.onNodeWithTag(CONNECTION_SECTION_TAG).assertDoesNotExist()
  }

  /** Common: Appearance, Connection, Token, Auto-refresh, Notifications, Home-screen scale. */
  @Test
  fun commonTab_order() {
    waitForContent()
    openTab(AppTab.COMMON)
    val appearance = top(compose.onNodeWithText(text(R.string.appearance_title)))
    val connection = top(compose.onNodeWithTag(CONNECTION_SECTION_TAG))
    val token = top(compose.onNodeWithText(text(R.string.token_title)))
    val refresh = top(compose.onNodeWithText(text(R.string.refresh_title)))
    val notify = top(compose.onNodeWithText(text(R.string.notify_title)))
    val scale = top(compose.onNodeWithText(text(R.string.widget_scale_label)))
    assertTrue(appearance < connection && connection < token && token < refresh)
    assertTrue(refresh < notify && notify < scale)
    compose.onNodeWithTag(WIDGET_PREVIEW_TAG).assertDoesNotExist()
  }

  /**
   * Inverter: preview, "Add to home screen" (when the launcher can pin), Display, Icons, Levels and
   * alerts, Entities; no battery or shared section.
   */
  @Test
  fun inverterTab_order() {
    waitForContent()
    openTab(AppTab.INVERTER)
    val preview = top(compose.onNodeWithTag(STRIP_PREVIEW_TAG))
    val add =
      compose.onAllNodesWithText(text(R.string.action_add_widget)).fetchSemanticsNodes().firstOrNull()
    val display = top(compose.onNodeWithText(text(R.string.inverter_display_title)))
    val icons = top(compose.onNodeWithText(text(R.string.inverter_icons_title)))
    val colors = top(compose.onNodeWithText(text(R.string.colors_title)))
    val entities = top(compose.onNodeWithText(text(R.string.entities_title)))
    add?.positionInRoot?.y?.let { assertTrue(preview < it && it < display) }
    assertTrue(preview < display && display < icons && icons < colors && colors < entities)
    compose.onNodeWithTag(WIDGET_PREVIEW_TAG).assertDoesNotExist()
    compose.onNodeWithText(text(R.string.widget_settings_title)).assertDoesNotExist()
    compose.onNodeWithTag(CONNECTION_SECTION_TAG).assertDoesNotExist()
  }

  /** A tab left and opened again keeps its preview size tab, its folds and its scroll. */
  @Test
  fun tabSwitch_keepsTheTabsState() {
    waitForContent()
    openTab(AppTab.BATTERY)
    val fiveByThree = compose.activity.getString(R.string.preview_size, 5, 3)
    compose.onNodeWithText(fiveByThree).performClick()
    val pack = compose.onNodeWithText(text(R.string.pack_title))
    pack.performScrollTo().performClick()
    compose.waitForIdle()
    val scrolledTop = top(pack)

    openTab(AppTab.COMMON)
    compose.onNodeWithText(text(R.string.pack_title)).assertDoesNotExist()
    openTab(AppTab.BATTERY)

    compose.onNodeWithText(text(R.string.capacity_label)).assertExists()
    assertEquals(scrolledTop, top(compose.onNodeWithText(text(R.string.pack_title))), 1f)
    compose.onNodeWithText(fiveByThree).assertIsSelected()
  }

  private fun storedSettings() =
    runBlocking {
      ApplicationProvider.getApplicationContext<App>().container.settingsRepository.settings.first()
    }

  private val tokenStore get() = ApplicationProvider.getApplicationContext<App>().container.tokenStore

  private fun tokenSummary(@StringRes state: Int) =
    compose.activity.getString(R.string.token_summary_with_type, text(state), text(R.string.token_type_user))

  /** A fresh install has no token, so the token section starts unfolded. */
  @Test
  fun tokenType_switchAndRemove() {
    waitForContent()
    openTab(AppTab.COMMON)
    compose.onNodeWithText(text(R.string.token_type_user)).performScrollTo().performClick()
    compose.waitUntil(LOAD_TIMEOUT_MS) { storedSettings().connection.tokenType == TokenType.USER }

    runBlocking {
      tokenStore.set(TokenType.ADMIN, "admin-token")
      tokenStore.set(TokenType.USER, "user-token")
    }
    compose.waitUntil(LOAD_TIMEOUT_MS) {
      compose.onAllNodesWithText(tokenSummary(R.string.token_summary_saved)).fetchSemanticsNodes().isNotEmpty()
    }
    compose.onNodeWithText(text(R.string.token_remove)).performScrollTo().performClick()
    compose.onNodeWithText(text(R.string.token_remove_title_user)).assertIsDisplayed()
    compose.onNodeWithText(text(R.string.token_remove_confirm)).performClick()

    compose.waitUntil(LOAD_TIMEOUT_MS) {
      compose.onAllNodesWithText(tokenSummary(R.string.summary_not_set)).fetchSemanticsNodes().isNotEmpty()
    }
    compose.onNodeWithText(text(R.string.token_remove)).assertDoesNotExist()
    assertEquals(setOf(TokenType.ADMIN), runBlocking { tokenStore.savedTypes.first() })
  }

  private fun tokenFieldText(): String =
    compose
      .onNodeWithText(text(R.string.token_label))
      .fetchSemanticsNode()
      .config[SemanticsProperties.EditableText]
      .text

  private fun tapTokenType(type: TokenType, @StringRes label: Int) {
    compose.onNodeWithText(text(label)).performScrollTo().performClick()
    compose.waitUntil(LOAD_TIMEOUT_MS) { storedSettings().connection.tokenType == type }
    compose.waitForIdle()
  }

  /** A draft typed for one type must never reach the other type's slot. */
  @Test
  fun tokenType_tapDropsUnsavedDraft() {
    waitForContent()
    openTab(AppTab.COMMON)
    compose
      .onNodeWithText(text(R.string.token_label))
      .performScrollTo()
      .performTextReplacement("draft-token")
    assertTrue(tokenFieldText().isNotEmpty())

    tapTokenType(TokenType.USER, R.string.token_type_user)
    assertEquals("", tokenFieldText())

    tapTokenType(TokenType.ADMIN, R.string.token_type_admin)
    assertEquals("", tokenFieldText())
    assertEquals(emptySet<TokenType>(), runBlocking { tokenStore.savedTypes.first() })
  }

  @Test
  fun scaleSlider_savesWhenReleased() {
    waitForContent()
    openTab(AppTab.COMMON)
    compose.onNodeWithText(text(R.string.widget_scale_label)).performScrollTo().performClick()
    val slider = compose.onNodeWithContentDescription(text(R.string.widget_scale_label))
    slider.performScrollTo()
    // Inside the track: the semantics bounds are wider than the draggable area.
    slider.performTouchInput {
      down(center)
      moveTo(centerRight)
    }
    compose.waitForIdle()
    assertEquals(HomeScale.DEFAULT, storedSettings().homeScalePercent)
    slider.performTouchInput { up() }
    compose.waitUntil(LOAD_TIMEOUT_MS) { storedSettings().homeScalePercent == HomeScale.MAX }
    compose.onNodeWithText(text(R.string.widget_reset_scale)).performScrollTo().performClick()
  }

  private fun storeFontsAndScale(fonts: FontPercents, scale: Int) =
    runBlocking {
      ApplicationProvider.getApplicationContext<App>().container.settingsRepository.update {
        it.copy(fontPercents = fonts, homeScalePercent = scale)
      }
    }

  @Test
  fun resetSizes_setsFontsTo50_keepsTheScale() {
    waitForContent()
    storeFontsAndScale(FontPercents(soc = 90, small = 30), 128)
    openTab(AppTab.BATTERY)
    compose.onNodeWithText(text(R.string.widget_settings_title)).performScrollTo().performClick()
    compose.onNodeWithText(text(R.string.widget_reset_sizes)).performScrollTo().performClick()
    compose.waitUntil(LOAD_TIMEOUT_MS) { storedSettings().fontPercents == FontPercents() }
    assertEquals(128, storedSettings().homeScalePercent)
  }

  @Test
  fun resetScale_setsTheScaleTo100_keepsTheFonts() {
    waitForContent()
    val fonts = FontPercents(soc = 90, small = 30)
    storeFontsAndScale(fonts, 128)
    openTab(AppTab.COMMON)
    compose.onNodeWithText(text(R.string.widget_scale_label)).performScrollTo().performClick()
    compose.onNodeWithText(text(R.string.widget_reset_scale)).performScrollTo().performClick()
    compose.waitUntil(LOAD_TIMEOUT_MS) { storedSettings().homeScalePercent == HomeScale.DEFAULT }
    assertEquals(fonts, storedSettings().fontPercents)
  }

  /** A pixel at the left edge, half way down: the screen's background. */
  private fun backgroundPixel(): Color {
    val pixels = compose.onRoot().captureToImage().toPixelMap()
    return pixels[2, pixels.height / 2]
  }

  /** A pixel in the bottom padding of the widget preview: the widget card's colour. */
  private fun cardPixel(): Color {
    val pixels = compose.onNodeWithTag(WIDGET_PREVIEW_TAG).captureToImage().toPixelMap()
    return pixels[pixels.width / 2, pixels.height - 6]
  }

  /**
   * The theme changes the colours in a moment: wait for them, do not read once. The preview lives
   * on the Battery tab, so this opens it.
   */
  private fun waitForTheme(dark: Boolean) {
    openTab(AppTab.BATTERY)
    val card = if (dark) WidgetPalette.Dark.card else WidgetPalette.Light.card
    compose.waitUntil(LOAD_TIMEOUT_MS) {
      val background = backgroundPixel().luminance()
      cardPixel().isCloseTo(card) && if (dark) background < 0.3f else background > 0.7f
    }
  }

  @Test
  fun themeSwitch_changesTheAppColours() {
    waitForContent()
    openTab(AppTab.COMMON)
    compose.onNodeWithText(text(R.string.appearance_title)).performScrollTo().performClick()

    compose.onNodeWithText(text(R.string.appearance_theme_dark)).performScrollTo().performClick()
    waitForTheme(dark = true)

    // Common keeps Appearance unfolded while the Battery tab is shown.
    openTab(AppTab.COMMON)
    compose.onNodeWithText(text(R.string.appearance_theme_light)).performScrollTo().performClick()
    waitForTheme(dark = false)

    // Leave the stored choice as a fresh install has it.
    // The theme row comes first; the language row (Android 13+) has a System button too.
    openTab(AppTab.COMMON)
    compose.onAllNodesWithText(text(R.string.appearance_system))[0].performScrollTo().performClick()
  }

  @Test
  fun languageRow_existsOnlyFromAndroid13() {
    waitForContent()
    openTab(AppTab.COMMON)
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
    val before = language.current()
    openTab(AppTab.COMMON)
    compose.onNodeWithText(text(R.string.appearance_title)).performScrollTo().performClick()
    try {
      compose.onNodeWithText(text(R.string.appearance_language_ukrainian)).performScrollTo().performClick()
      // The system recreates the activity; the screen then speaks Ukrainian.
      compose.waitUntil(LOAD_TIMEOUT_MS) {
        compose.onAllNodesWithText(UKRAINIAN_APPEARANCE).fetchSemanticsNodes().isNotEmpty()
      }
      assertEquals(LanguageChoice.UKRAINIAN, language.current())
    } finally {
      // The language lives in the system, not in the saved settings: put it back.
      language.set(before)
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
    /** `appearance_title` in Ukrainian, written out: the test runs in the device's language. */
    const val UKRAINIAN_APPEARANCE = "Вигляд"
  }
}
