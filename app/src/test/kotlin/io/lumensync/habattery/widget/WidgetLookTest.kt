package io.lumensync.habattery.widget

import android.content.Context
import android.util.TypedValue
import androidx.test.core.app.ApplicationProvider
import io.lumensync.habattery.domain.model.FontGroup
import io.lumensync.habattery.domain.model.FontSizes
import io.lumensync.habattery.format.Formatters
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The sp handed to Glance must show at the capped size, also with Android 14+ non-linear sp. */
@RunWith(RobolectricTestRunner::class)
class WidgetLookTest {
  private val context: Context
    get() = ApplicationProvider.getApplicationContext()

  private fun look(s: Float = 1f) =
    WidgetLook(
      s,
      FontSizes(),
      context.resources.configuration.fontScale,
      context.resources.displayMetrics,
      WidgetTexts(context, Formatters(context)),
    )

  /** What the system shows for the sp Glance gets, in dp. */
  private fun shownDp(look: WidgetLook, group: FontGroup): Float {
    val metrics = context.resources.displayMetrics
    return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, look.font(group).value, metrics) /
      metrics.density
  }

  @Test
  @Config(fontScale = 1.3f)
  fun largeSystemText_showsTheCappedSize() {
    assertEquals(42f, shownDp(look(), FontGroup.SOC), 0.05f)
    assertEquals(24f, shownDp(look(), FontGroup.MAIN), 0.05f)
    assertEquals(12f, shownDp(look(), FontGroup.SMALL), 0.05f)
  }

  @Test
  @Config(fontScale = 1.1f)
  fun slightlyLargerText_growsBelowTheCap() =
    assertEquals(10.5f * 1.1f, shownDp(look(), FontGroup.SMALL), 0.05f)

  @Test
  fun defaultText_scalesWithTheWidget() = assertEquals(40f * 0.8f, shownDp(look(0.8f), FontGroup.SOC), 0.05f)
}
