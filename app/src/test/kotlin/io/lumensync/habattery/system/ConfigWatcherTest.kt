package io.lumensync.habattery.system

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigWatcherTest {
  private val en = TextConfig("en-US", 1f)

  @Test
  fun sameConfig_changesNothing() {
    val watcher = ConfigWatcher(en)
    assertFalse(watcher.changed(TextConfig("en-US", 1f)))
    assertEquals(en, watcher.config.value)
  }

  @Test
  fun newTags_areRememberedAndReportedOnce() {
    val watcher = ConfigWatcher(en)
    assertTrue(watcher.changed(TextConfig("uk-UA", 1f)))
    assertEquals("uk-UA", watcher.config.value.languageTags)
    assertFalse(watcher.changed(TextConfig("uk-UA", 1f)))
    assertTrue(watcher.changed(en))
  }

  /** Review of spec 0005, finding 6: a new system font scale redraws the widgets too. */
  @Test
  fun newFontScale_isRememberedAndReportedOnce() {
    val watcher = ConfigWatcher(en)
    assertTrue(watcher.changed(TextConfig("en-US", 1.3f)))
    assertEquals(1.3f, watcher.config.value.fontScale)
    assertFalse(watcher.changed(TextConfig("en-US", 1.3f)))
  }
}
