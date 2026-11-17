package io.lumensync.habattery.system

import android.content.res.Configuration.UI_MODE_NIGHT_NO
import android.content.res.Configuration.UI_MODE_NIGHT_YES
import android.content.res.Configuration.UI_MODE_TYPE_CAR
import android.content.res.Configuration.UI_MODE_TYPE_NORMAL
import android.os.Build
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

  /** Before Android 12 Glance picks the day or the night colour when it draws. */
  @Test
  fun nightSwitch_beforeAndroid12_isAChange() {
    val watcher = ConfigWatcher(config(UI_MODE_NIGHT_NO, Build.VERSION_CODES.R))
    assertTrue(watcher.changed(config(UI_MODE_NIGHT_YES, Build.VERSION_CODES.R)))
    assertFalse(watcher.changed(config(UI_MODE_NIGHT_YES, Build.VERSION_CODES.R)))
  }

  /** From Android 12 the home screen picks the colour: a switch redraws nothing. */
  @Test
  fun nightSwitch_fromAndroid12_isNoChange() {
    val watcher = ConfigWatcher(config(UI_MODE_NIGHT_NO, Build.VERSION_CODES.S))
    assertFalse(watcher.changed(config(UI_MODE_NIGHT_YES, Build.VERSION_CODES.S)))
  }

  /** Only the night bits count: a car or desk dock changes nothing. */
  @Test
  fun otherUiModeBits_areNoChange() {
    val watcher = ConfigWatcher(config(UI_MODE_NIGHT_NO, Build.VERSION_CODES.R))
    assertFalse(watcher.changed(config(UI_MODE_NIGHT_NO, Build.VERSION_CODES.R, UI_MODE_TYPE_CAR)))
  }

  private fun config(night: Int, sdk: Int, type: Int = UI_MODE_TYPE_NORMAL) =
    TextConfig.of("en-US", 1f, type or night, sdk)
}
