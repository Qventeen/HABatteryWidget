package io.lumensync.habattery.system

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The language tags the app shows now (spec 0008). [App] reports every configuration change; only
 * a real change of the tags counts, so rotation and other changes do nothing. The widget reads
 * [tags] to start a new composition: a live Glance session only gets its state updated by
 * `update()`, it does not run `provideGlance` again.
 */
class LocaleWatcher(initialTags: String) {
  private val state = MutableStateFlow(initialTags)
  val tags: StateFlow<String> = state

  /** Remembers [newTags]; true when they differ from the ones before (the widgets must redraw). */
  @Synchronized
  fun changed(newTags: String): Boolean {
    if (state.value == newTags) return false
    state.value = newTags
    return true
  }
}
