package io.lumensync.habattery.system

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** What the widget's drawing takes from the configuration: the language and the font scale. */
data class TextConfig(val languageTags: String, val fontScale: Float)

/**
 * The language tags and the system font scale the app shows now (specs 0008, 0005). [App] reports
 * every configuration change; only a real change of these counts, so rotation and other changes do
 * nothing. The widget reads [config] to start a new composition: a live Glance session only gets
 * its state updated by `update()`, it does not run `provideGlance` again, and the texts, their
 * fitted sizes and the sp handed to Glance all come from the configuration.
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
