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
