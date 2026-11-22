package io.lumensync.habattery.ui.sections

import android.content.Context
import android.content.res.Resources
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class CellsHintTest {
  private val resources: Resources
    get() = ApplicationProvider.getApplicationContext<Context>().resources

  @Test
  @Config(qualifiers = "en")
  fun counted_en_namesTheCount() = assertEquals("From sensors: 16", cellsHint(resources, 16))

  @Test
  @Config(qualifiers = "uk")
  fun counted_uk_namesTheCount() = assertEquals("За датчиками: 16", cellsHint(resources, 16))

  /** The info tip explains when the typed count is used, so nothing shows under the field. */
  @Test fun notCounted_showsNothing() = assertNull(cellsHint(resources, null))
}
