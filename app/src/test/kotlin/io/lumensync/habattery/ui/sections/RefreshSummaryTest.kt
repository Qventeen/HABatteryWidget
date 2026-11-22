package io.lumensync.habattery.ui.sections

import android.content.Context
import android.content.res.Resources
import androidx.test.core.app.ApplicationProvider
import io.lumensync.habattery.domain.model.RefreshSettings
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class RefreshSummaryTest {
  private val resources: Resources
    get() = ApplicationProvider.getApplicationContext<Context>().resources

  private fun summary(auto: Boolean, onUnlock: Boolean) =
    refreshSummary(resources, RefreshSettings(auto = auto, intervalMinutes = 30, onUnlock = onUnlock))

  @Test
  @Config(qualifiers = "en")
  fun timedOn_en_namesTheInterval() =
    assertEquals("Every 30 min", summary(auto = true, onUnlock = true))

  @Test
  @Config(qualifiers = "en")
  fun onlyUnlock_en() = assertEquals("On unlock", summary(auto = false, onUnlock = true))

  @Test
  @Config(qualifiers = "uk")
  fun onlyUnlock_uk() = assertEquals("Після розблокування", summary(auto = false, onUnlock = true))

  @Test
  @Config(qualifiers = "en")
  fun bothOff_en() = assertEquals("Off", summary(auto = false, onUnlock = false))
}
