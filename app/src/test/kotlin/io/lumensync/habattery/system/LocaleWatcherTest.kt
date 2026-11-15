package io.lumensync.habattery.system

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocaleWatcherTest {
  @Test
  fun sameTags_changeNothing() {
    val watcher = LocaleWatcher("en-US")
    assertFalse(watcher.changed("en-US"))
    assertEquals("en-US", watcher.tags.value)
  }

  @Test
  fun newTags_areRememberedAndReportedOnce() {
    val watcher = LocaleWatcher("en-US")
    assertTrue(watcher.changed("uk-UA"))
    assertEquals("uk-UA", watcher.tags.value)
    assertFalse(watcher.changed("uk-UA"))
    assertTrue(watcher.changed("en-US"))
  }
}
