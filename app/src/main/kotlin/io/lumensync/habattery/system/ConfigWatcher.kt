package io.lumensync.habattery.system

import android.content.res.Configuration
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * What the widget's drawing takes from the configuration: the language, the font scale and,
 * before Android 12, the night mode (the `UI_MODE_NIGHT_*` bits of `uiMode`). Before Android 12
 * Glance picks the day or the night colour when it draws, so a switch must redraw the widgets.
 * From Android 12 the widget carries both colours and the home screen picks one: [nightMode]
 * stays `UI_MODE_NIGHT_UNDEFINED`, and a switch redraws nothing.
 */
data class TextConfig(
  val languageTags: String,
  val fontScale: Float,
  val nightMode: Int = Configuration.UI_MODE_NIGHT_UNDEFINED,
) {
  companion object {
    fun of(config: Configuration): TextConfig =
      of(config.locales.toLanguageTags(), config.fontScale, config.uiMode, Build.VERSION.SDK_INT)

    internal fun of(languageTags: String, fontScale: Float, uiMode: Int, sdk: Int): TextConfig =
      TextConfig(
        languageTags,
        fontScale,
        if (sdk < Build.VERSION_CODES.S) uiMode and Configuration.UI_MODE_NIGHT_MASK
        else Configuration.UI_MODE_NIGHT_UNDEFINED,
      )
  }
}

/**
 * The parts of the configuration the widget's drawing takes ([TextConfig]) as the app shows them
 * now (specs 0008, 0005). [App] reports every configuration change; only a real change of these
 * counts, so rotation and other changes do nothing. The widget reads [config] to start a new
 * composition: a live Glance session only gets its state updated by `update()`, it does not run
 * `provideGlance` again, and the texts, their fitted sizes and the sp handed to Glance all come
 * from the configuration.
 */
class ConfigWatcher(initial: TextConfig) {
  private val state = MutableStateFlow(initial)
  val config: StateFlow<TextConfig> = state

  /** Remembers [new]; true when it differs from the one before (the widgets must redraw). */
  @Synchronized
  fun changed(new: TextConfig): Boolean {
    if (state.value == new) return false
    state.value = new
    return true
  }
}
