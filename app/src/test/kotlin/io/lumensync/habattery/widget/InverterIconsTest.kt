package io.lumensync.habattery.widget

import android.content.Context
import android.content.res.Configuration
import androidx.core.content.res.ResourcesCompat
import androidx.test.core.app.ApplicationProvider
import io.lumensync.habattery.domain.model.InverterIcon
import java.util.Locale
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class InverterIconsTest {
  private val context: Context
    get() = ApplicationProvider.getApplicationContext()

  private fun inLocale(tag: String): Context {
    val config = Configuration(context.resources.configuration).apply { setLocale(Locale.forLanguageTag(tag)) }
    return context.createConfigurationContext(config)
  }

  @Test
  fun everyIcon_hasDrawableAndLabel() {
    val en = inLocale("en")
    val uk = inLocale("uk")
    for (icon in InverterIcon.entries) {
      assertNotNull("$icon drawable", ResourcesCompat.getDrawable(context.resources, icon.drawable(), null))
      assertFalse("$icon en", en.getString(icon.label()).isBlank())
      assertFalse("$icon uk", uk.getString(icon.label()).isBlank())
    }
  }
}
