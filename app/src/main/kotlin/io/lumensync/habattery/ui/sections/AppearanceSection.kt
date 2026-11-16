package io.lumensync.habattery.ui.sections

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import io.lumensync.habattery.R
import io.lumensync.habattery.domain.model.AppearanceSettings
import io.lumensync.habattery.domain.model.LanguageChoice
import io.lumensync.habattery.domain.model.ThemeChoice

/** Test tag of the language row; the row is missing before Android 13. */
const val LANGUAGE_ROW_TAG = "appearance-language"

/**
 * Theme and language of the app and the widget. A choice applies at once, no
 * Save. [language] is null where the system has no per-app language (before Android 13): the
 * row is hidden then.
 */
@Composable
fun AppearanceSection(
  appearance: AppearanceSettings,
  language: LanguageChoice?,
  onSaveAppearance: (AppearanceSettings) -> Unit,
  onSetLanguage: (LanguageChoice) -> Unit,
  modifier: Modifier = Modifier,
  initiallyExpanded: Boolean = false,
) {
  val theme = stringResource(themeSummary(appearance.theme))
  val summary =
    if (language == null) theme
    else stringResource(R.string.appearance_summary, theme, stringResource(languageSummary(language)))

  SectionCard(R.string.appearance_title, summary, initiallyExpanded, modifier) {
    ChoiceRow(R.string.appearance_theme, ThemeChoice.entries, appearance.theme, ::themeLabel) {
      onSaveAppearance(appearance.copy(theme = it))
    }
    if (language != null) {
      ChoiceRow(
        R.string.appearance_language,
        LanguageChoice.entries,
        language,
        ::languageLabel,
        Modifier.testTag(LANGUAGE_ROW_TAG),
        onSetLanguage,
      )
    }
  }
}

@StringRes
private fun themeLabel(choice: ThemeChoice): Int =
  when (choice) {
    ThemeChoice.SYSTEM -> R.string.appearance_system
    ThemeChoice.LIGHT -> R.string.appearance_theme_light
    ThemeChoice.DARK -> R.string.appearance_theme_dark
  }

@StringRes
private fun themeSummary(choice: ThemeChoice): Int =
  when (choice) {
    ThemeChoice.SYSTEM -> R.string.appearance_summary_theme_system
    ThemeChoice.LIGHT -> R.string.appearance_summary_theme_light
    ThemeChoice.DARK -> R.string.appearance_summary_theme_dark
  }

/** The names of the languages are in their own language, in every locale. */
@StringRes
private fun languageLabel(choice: LanguageChoice): Int =
  when (choice) {
    LanguageChoice.SYSTEM -> R.string.appearance_system
    LanguageChoice.ENGLISH -> R.string.appearance_language_english
    LanguageChoice.UKRAINIAN -> R.string.appearance_language_ukrainian
  }

@StringRes
private fun languageSummary(choice: LanguageChoice): Int =
  when (choice) {
    LanguageChoice.SYSTEM -> R.string.appearance_summary_language_system
    LanguageChoice.ENGLISH -> R.string.appearance_language_english
    LanguageChoice.UKRAINIAN -> R.string.appearance_language_ukrainian
  }
