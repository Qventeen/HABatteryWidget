package io.lumensync.habattery.ui

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.lumensync.habattery.R
import io.lumensync.habattery.domain.BatteryOverview
import io.lumensync.habattery.domain.WidgetModel
import io.lumensync.habattery.domain.model.BatteryReading
import io.lumensync.habattery.domain.model.CacheState
import io.lumensync.habattery.domain.model.ConnectionSettings
import io.lumensync.habattery.domain.model.EntitySettings
import io.lumensync.habattery.domain.model.FontPercents
import io.lumensync.habattery.domain.model.Settings
import io.lumensync.habattery.domain.model.SyncError
import io.lumensync.habattery.domain.model.ThemeChoice
import io.lumensync.habattery.format.Formatters
import io.lumensync.habattery.ui.sections.WIDGET_PREVIEW_TAG
import io.lumensync.habattery.ui.sections.WidgetPreviewCard
import io.lumensync.habattery.ui.theme.HaBatteryTheme
import io.lumensync.habattery.widget.WidgetPalette
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The app's widget preview (spec 0006, §6): the real widget views per size tab, tab switching, the
 * empty state, the theme, and a narrow screen. The widget's texts live in Android views (applied
 * RemoteViews), so they are read from the view tree, not from Compose semantics.
 */
@RunWith(AndroidJUnit4::class)
class WidgetPreviewTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  private val context
    get() = compose.activity

  private val settings =
    Settings(
      connection = ConnectionSettings("http://10.0.2.2:8123"),
      entities = EntitySettings(charge = "sensor.soc", temperatures = listOf("sensor.t1")),
    )

  private val now = System.currentTimeMillis()

  private fun model(reading: BatteryReading?, error: SyncError? = null, hasToken: Boolean = true): WidgetModel =
    WidgetModel.build(BatteryOverview.build(CacheState(reading, error), settings, hasToken, now), settings)

  private val sample =
    BatteryReading(
      chargePercent = 54.6,
      currentA = -10.0,
      totalVoltageV = 26.5,
      capacityRemainingAh = 157.0,
      temperaturesC = listOf(25.0),
      updatedAtMillis = now - 60_000L,
    )

  private fun show(model: WidgetModel, firstTab: PreviewTab = PreviewTab.TAB_4X2, width: Dp? = null) {
    compose.setContent {
      HaBatteryTheme {
        Box(if (width == null) Modifier else Modifier.width(width)) {
          WidgetPreviewCard(model, FontPercents(), ThemeChoice.SYSTEM, firstTab)
        }
      }
    }
  }

  private fun label(tab: PreviewTab) = context.getString(R.string.preview_size, tab.columns, tab.rows)

  /** Every text of the applied widget views (Compose texts are not Android text views). */
  private fun widgetTexts(): List<String> {
    val found = mutableListOf<String>()
    fun visit(view: View) {
      if (view is TextView && view.visibility == View.VISIBLE) found += view.text.toString()
      if (view is ViewGroup) (0 until view.childCount).forEach { visit(view.getChildAt(it)) }
    }
    compose.runOnUiThread { visit(context.window.decorView) }
    return found
  }

  private fun waitForWidgetText(text: String) =
    compose.waitUntil(TIMEOUT_MS) { text in widgetTexts() }

  /** The preview box takes the shown layout's size once its views are applied. */
  private fun waitForSize(size: DpSize) =
    compose.waitUntil(TIMEOUT_MS) {
      runCatching {
          compose
            .onNodeWithTag(WIDGET_PREVIEW_TAG)
            .assertWidthIsEqualTo(size.width)
            .assertHeightIsEqualTo(size.height)
        }
        .isSuccess
    }

  @Test
  fun eachTab_showsTheWidgetAtItsDesignSize() {
    show(model(sample))
    PreviewTab.entries.forEach { tab ->
      compose.onNodeWithText(label(tab)).performClick()
      compose.onNodeWithText(label(tab)).assertIsSelected()
      waitForSize(tab.layout.designSize)
      waitForWidgetText("55")
      assertTrue(tab.name, Formatters(context).voltage(26.5) in widgetTexts())
    }
  }

  @Test
  fun tabsSwitch_backAndForth() {
    show(model(sample), firstTab = PreviewTab.TAB_4X3)
    compose.onNodeWithText(label(PreviewTab.TAB_4X3)).assertIsSelected()
    waitForSize(PreviewTab.TAB_4X3.layout.designSize)
    // 4×3 has its own root type: switching away and back draws fresh views each time.
    listOf(PreviewTab.TAB_5X3, PreviewTab.TAB_4X3, PreviewTab.TAB_4X2, PreviewTab.TAB_4X3).forEach { tab ->
      compose.onNodeWithText(label(tab)).performClick()
      waitForSize(tab.layout.designSize)
      waitForWidgetText("55")
    }
  }

  @Test
  fun emptyModel_showsTheMessageWithoutOpenApp() {
    show(model(null, hasToken = false))
    waitForWidgetText(context.getString(R.string.error_not_configured))
    assertFalse(context.getString(R.string.widget_open_app) in widgetTexts())
    compose.onNodeWithText(context.getString(R.string.overview_setup)).assertExists()
  }

  /** The widget shows only the error icon; the full reason is under the preview. */
  @Test
  fun syncError_fullReasonUnderThePreview() {
    show(model(sample, SyncError.Unreachable))
    waitForWidgetText("55")
    val reason = Formatters(context).error(SyncError.Unreachable)
    compose.onNodeWithText(context.getString(R.string.widget_sync_error, reason)).assertExists()
  }

  /** At 300 dp the 340 dp wide 5×3 shrinks to the width and keeps its shape. */
  @Test
  fun narrowWidth_fiveByThreeFits() {
    show(model(sample), firstTab = PreviewTab.TAB_5X3, width = 300.dp)
    val design = PreviewTab.TAB_5X3.layout.designSize
    waitForSize(DpSize(300.dp, design.height * (300f / design.width.value)))
    waitForWidgetText("55")
  }

  /** The app's theme wins over the device's mode: Dark and Light draw their own card. */
  @Test
  fun appTheme_drawsItsCard() {
    var theme by mutableStateOf(ThemeChoice.DARK)
    compose.setContent {
      HaBatteryTheme { WidgetPreviewCard(model(sample), FontPercents(), theme, PreviewTab.TAB_4X2) }
    }
    waitForCard(WidgetPalette.Dark.card)
    theme = ThemeChoice.LIGHT
    waitForCard(WidgetPalette.Light.card)
  }

  /** Bottom middle of the widget card: its padding, no content on top. */
  private fun waitForCard(expected: Color) =
    compose.waitUntil(TIMEOUT_MS) {
      val pixels = compose.onNodeWithTag(WIDGET_PREVIEW_TAG).captureToImage().toPixelMap()
      pixels[pixels.width / 2, pixels.height - 6].isCloseTo(expected)
    }

  private companion object {
    const val TIMEOUT_MS = 10_000L
  }
}
