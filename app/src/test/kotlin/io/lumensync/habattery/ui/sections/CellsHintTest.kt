package io.lumensync.habattery.ui.sections

import android.content.Context
import android.content.res.Resources
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
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
  fun counted_en_namesTheCount() {
    val rule = "This number is used only when the total voltage or average cell sensor is empty."
    assertEquals("The sensors count 16 cells now. $rule", cellsHint(resources, 16))
    assertEquals("The sensors count 1 cell now. $rule", cellsHint(resources, 1))
  }

  @Test
  @Config(qualifiers = "en")
  fun notCounted_en_saysWhenTheTypedCountIsUsed() =
    assertEquals("Used when the total voltage or average cell sensor is empty.", cellsHint(resources, null))

  @Test
  @Config(qualifiers = "uk")
  fun counted_uk_usesThePluralForm() {
    val rule = "Це число використовується, лише коли датчик загальної напруги або середньої комірки порожній."
    assertEquals("За датчиками зараз 1 комірка. $rule", cellsHint(resources, 1))
    assertEquals("За датчиками зараз 4 комірки. $rule", cellsHint(resources, 4))
    assertEquals("За датчиками зараз 16 комірок. $rule", cellsHint(resources, 16))
    assertEquals("За датчиками зараз 21 комірка. $rule", cellsHint(resources, 21))
  }
}
