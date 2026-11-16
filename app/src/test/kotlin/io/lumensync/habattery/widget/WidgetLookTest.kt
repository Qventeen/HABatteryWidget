package io.lumensync.habattery.widget

import android.content.Context
import android.util.TypedValue
import androidx.test.core.app.ApplicationProvider
import io.lumensync.habattery.domain.model.ThemeChoice
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The sp handed to Glance must show at the shown size, also with Android 14+ non-linear sp. */
@RunWith(RobolectricTestRunner::class)
class WidgetLookTest {
  private val context: Context
    get() = ApplicationProvider.getApplicationContext()

  private fun look() =
    WidgetLook(
      LayoutSpec.L4x2,
      WidgetColors(ThemeChoice.SYSTEM),
      context.resources.displayMetrics,
      context.resources.configuration.fontScale,
    )

  /** What the system shows for the sp Glance gets, in dp. */
  private fun shownDp(dp: Float): Float {
    val metrics = context.resources.displayMetrics
    return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, look().sp(dp).value, metrics) / metrics.density
  }

  @Test fun normalText_showsTheSize() = assertEquals(28f, shownDp(28f), 0.05f)

  @Test @Config(fontScale = 1.3f) fun largeSystemText_isNotScaledTwice() = assertEquals(36.4f, shownDp(36.4f), 0.05f)

  @Test @Config(fontScale = 0.85f) fun smallSystemText_isNotScaledTwice() = assertEquals(8.925f, shownDp(8.925f), 0.05f)

  /**
   * A fitted size often sits just under a whole pixel, where the text gets wider at once. The
   * system's own sp → px math must never bring it back even a hair larger (1.3 on a Pixel 8:
   * 34.999996 px came back as 35.0 px, and "Δ 100 mV" grew from 149 to 156 px).
   */
  @Test @Config(fontScale = 1.3f, qualifiers = "420dpi")
  fun largeSystemText_neverShowsLarger() = assertNeverLarger()

  @Test @Config(fontScale = 0.85f, qualifiers = "420dpi")
  fun smallSystemText_neverShowsLarger() = assertNeverLarger()

  private fun assertNeverLarger() {
    val metrics = context.resources.displayMetrics
    val look = look()
    val larger =
      (1..200).map { Math.nextDown(it.toFloat()) / metrics.density }.filter { dp ->
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, look.sp(dp).value, metrics) > dp * metrics.density
      }
    assertEquals("shown larger than fitted at $larger dp", emptyList<Float>(), larger)
  }
}
