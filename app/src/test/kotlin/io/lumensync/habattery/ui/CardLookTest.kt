package io.lumensync.habattery.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.lumensync.habattery.domain.model.FontGroup
import io.lumensync.habattery.domain.model.FontPercents
import io.lumensync.habattery.format.Formatters
import io.lumensync.habattery.ui.sections.CardLook
import io.lumensync.habattery.widget.WidgetPalette
import io.lumensync.habattery.widget.WidgetTexts
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The app card: v4 size × font scale, capped at the v4 maximum, × percent / 50 (review finding 5). */
@RunWith(RobolectricTestRunner::class)
class CardLookTest {
  private val context: Context
    get() = ApplicationProvider.getApplicationContext()

  private fun look(percents: FontPercents, fontScale: Float) =
    CardLook(1f, percents, fontScale, WidgetPalette.Light, WidgetTexts(context, Formatters(context)))

  @Test
  fun fiftyPercent_normalScale_isTheV4Size() = assertEquals(40f, look(FontPercents(), 1f).shownSize(FontGroup.SOC), 1e-4f)

  @Test
  fun largeSystemFont_isCappedAtTheV4Maximum() {
    assertEquals(42f, look(FontPercents(), 1.3f).shownSize(FontGroup.SOC), 1e-4f)
    assertEquals(10.5f * 1.1f, look(FontPercents(), 1.1f).shownSize(FontGroup.SMALL), 1e-4f)
  }

  @Test
  fun percent_scalesTheCappedSize() = assertEquals(84f, look(FontPercents(soc = 100), 1.3f).shownSize(FontGroup.SOC), 1e-4f)
}
