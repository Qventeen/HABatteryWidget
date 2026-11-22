package io.lumensync.habattery.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.testing.unit.GlanceAppWidgetUnitTest
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.GlanceNodeMatcher
import androidx.glance.testing.unit.MappedNode
import androidx.glance.testing.unit.hasStartActivityClickAction
import androidx.glance.text.EmittableText
import androidx.test.core.app.ApplicationProvider
import io.lumensync.habattery.MainActivity
import io.lumensync.habattery.domain.BatteryOverview
import io.lumensync.habattery.domain.WidgetModel
import io.lumensync.habattery.domain.model.AppearanceSettings
import io.lumensync.habattery.domain.model.BatteryReading
import io.lumensync.habattery.domain.model.CacheState
import io.lumensync.habattery.domain.model.FontPercents
import io.lumensync.habattery.domain.model.Settings
import io.lumensync.habattery.domain.model.ThemeChoice
import io.lumensync.habattery.testing.configuredSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** What `BatteryWidget` hands to [WidgetContent] from the stored settings (specs 0006, 0007, 0008). */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-notnight")
class BatteryWidgetContentTest {
  private val context: Context
    get() = ApplicationProvider.getApplicationContext()

  private val cache = CacheState(BatteryReading(chargePercent = 54.6, updatedAtMillis = NOW - 60_000L), null)

  /** The app opens on the tapped widget's size: the tap carries the widget's id. */
  @Test
  fun tap_carriesTheTappedWidgetId() {
    val id = ActionParameters.Key<Int>(EXTRA_TAPPED_WIDGET_ID)
    render({ Widget(configuredSettings(), appWidgetId = 42) }) {
      onNode(hasStartActivityClickAction<MainActivity>(actionParametersOf(id to 42))).assertExists()
    }
  }

  @Test
  fun homeScale128_drawsAtScale1_28() {
    val settings = configuredSettings().copy(homeScalePercent = 128)
    val scaled = socStyle { Widget(settings) }
    assertEquals(socStyle { Content(settings, hostScale = 1.28f) }.fontSize, scaled.fontSize)
    assertNotEquals(socStyle { Content(settings, hostScale = 1f) }.fontSize, scaled.fontSize)
  }

  @Test
  fun fontPercents_reachTheDrawing() {
    val settings = configuredSettings().copy(fontPercents = FontPercents(soc = 30))
    val shown = socStyle { Widget(settings) }
    assertEquals(socStyle { Content(settings, FontPercents(soc = 30)) }.fontSize, shown.fontSize)
    assertNotEquals(socStyle { Content(settings, FontPercents()) }.fontSize, shown.fontSize)
  }

  /** By day the stored Dark theme still draws the dark palette. */
  @Test
  fun theme_reachesTheColours() {
    val settings = configuredSettings().copy(appearance = AppearanceSettings(ThemeChoice.DARK))
    assertEquals(WidgetPalette.Dark.text, socStyle { Widget(settings) }.color)
  }

  @Composable
  private fun Widget(settings: Settings, appWidgetId: Int = 1) =
    BatteryWidgetContent(cache, settings, hasToken = true, NOW, appWidgetId, FakeMeasurer)

  /** [WidgetContent] as the widget would call it, with the given font percents and scale. */
  @Composable
  private fun Content(settings: Settings, fontPercents: FontPercents = FontPercents(), hostScale: Float = 1f) {
    val model = WidgetModel.build(BatteryOverview.build(cache, settings, hasToken = true, NOW), settings)
    WidgetContent(model, fontPercents, WidgetColors(ThemeChoice.SYSTEM), null, hostScale, FakeMeasurer)
  }

  private class Style(val fontSize: TextUnit?, val color: Color?)

  /** The size and colour of the SOC number ("55"). */
  private fun socStyle(content: @Composable () -> Unit): Style {
    var style: Style? = null
    render(content) {
      onNode(
          GlanceNodeMatcher<MappedNode>("SOC number") { node ->
            val text = node.value.emittable as? EmittableText
            (text?.text == "55").also {
              if (it) style = Style(text!!.style?.fontSize, text.style?.color?.getColor(context))
            }
          }
        )
        .assertExists()
    }
    return checkNotNull(style)
  }

  private fun render(
    content: @Composable () -> Unit,
    assertions: GlanceAppWidgetUnitTest.() -> Unit,
  ) = runGlanceAppWidgetUnitTest {
    setContext(context)
    setAppWidgetSize(DpSize(LayoutSpec.L4x2.bucketWidth.dp, LayoutSpec.L4x2.bucketHeight.dp))
    provideComposable(content)
    assertions()
  }

  private companion object {
    const val NOW = 1_800_000_000_000L
  }
}
