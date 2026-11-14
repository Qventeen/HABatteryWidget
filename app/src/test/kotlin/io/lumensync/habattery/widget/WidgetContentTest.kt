package io.lumensync.habattery.widget

import android.content.ComponentName
import android.content.Context
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.testing.unit.GlanceAppWidgetUnitTest
import androidx.glance.appwidget.testing.unit.hasRunCallbackClickAction
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.hasAnyDescendant
import androidx.glance.testing.unit.hasClickAction
import androidx.glance.testing.unit.hasStartActivityClickAction
import androidx.glance.testing.unit.hasText
import androidx.glance.testing.unit.hasTextEqualTo
import androidx.test.core.app.ApplicationProvider
import io.lumensync.habattery.R
import io.lumensync.habattery.domain.BatteryOverview
import io.lumensync.habattery.domain.model.BatteryReading
import io.lumensync.habattery.domain.model.CacheState
import io.lumensync.habattery.domain.model.SyncError
import io.lumensync.habattery.format.Formatters
import io.lumensync.habattery.testing.configuredSettings
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en")
class WidgetContentTest {
  private val context: Context
    get() = ApplicationProvider.getApplicationContext()

  private val formatters: Formatters
    get() = Formatters(context)

  private val openAppComponent = ComponentName("io.lumensync.habattery", "OpenApp")

  @Test
  fun setupState_showsSetupText() =
    render(overview(hasToken = false)) {
      onNode(hasText(context.getString(R.string.error_not_configured))).assertExists()
      onAllNodes(hasText("55%")).assertCountEquals(0)
      // The only tap target is the card, and it opens the app: no ↻ in setup.
      onNode(hasClickAction()).assert(hasStartActivityClickAction(openAppComponent))
    }

  @Test
  fun noReading_showsNoData() =
    render(overview(reading = null)) {
      onNode(hasText(context.getString(R.string.widget_no_data))).assertExists()
      onNode(hasRunCallbackClickAction<NoOpRefresh>())
        .assert(hasAnyDescendant(hasText(context.getString(R.string.widget_refresh_symbol))))
    }

  @Test
  fun normal_showsChargeAndCells() =
    render(overview()) {
      onNode(hasText("55%")).assertExists()
      onNode(hasText("3.300")).assertExists()
      onNode(hasTextEqualTo(formatters.power(-207.0))).assertExists()
      onNode(hasTextEqualTo(formatters.current(-7.8))).assertExists()
      onNode(hasTextEqualTo(formatters.voltage(26.5))).assertExists()
      onNode(hasRunCallbackClickAction<NoOpRefresh>())
        .assert(hasAnyDescendant(hasText(context.getString(R.string.widget_refresh_symbol))))
    }

  @Test
  fun summaryTier_hidesCells() =
    render(overview(), heightDp = 110) {
      onNode(hasText("55%")).assertExists()
      onAllNodes(hasText("3.300")).assertCountEquals(0)
    }

  @Test
  fun missingPower_hidesPowerLine() =
    render(overview(sample(powerW = null))) {
      onNode(hasTextEqualTo(context.getString(R.string.status_unknown))).assertExists()
      onAllNodes(hasText(" W")).assertCountEquals(0)
    }

  @Test
  fun missingCurrentAndVoltage_hideTheirLines() =
    render(overview(sample(currentA = null, totalVoltageV = null))) {
      onNode(hasTextEqualTo(formatters.power(-207.0))).assertExists()
      onAllNodes(hasTextEqualTo(formatters.current(-7.8))).assertCountEquals(0)
      onAllNodes(hasTextEqualTo(formatters.voltage(26.5))).assertCountEquals(0)
    }

  @Test
  fun missingCell_showsDash() =
    render(overview(sample(cellVoltagesV = listOf(3.300, null, 3.302)))) {
      onNode(hasTextEqualTo(formatters.cellVoltage(null))).assertExists()
      onNode(hasText("3.302")).assertExists()
    }

  @Test
  fun error_showsErrorLine() =
    render(overview(error = SyncError.Unreachable)) {
      onNode(hasText(context.getString(R.string.error_unreachable))).assertExists()
      onNode(hasText("55%")).assertExists()
    }

  @Test
  fun refreshing_showsRefreshingText() =
    render(overview(refreshingSinceMillis = NOW - 1_000L)) {
      onNode(hasText(context.getString(R.string.widget_refreshing))).assertExists()
      onAllNodes(hasText(context.getString(R.string.widget_refresh_symbol))).assertCountEquals(0)
    }

  @Test
  fun summaryOnlyTier_110dp_hidesCells() =
    assertCellsAtHeight(110, WidgetDensityTier.SUMMARY_ONLY, shown = false)

  @Test
  fun compactTier_160dp_showsCells() =
    assertCellsAtHeight(160, WidgetDensityTier.COMPACT, shown = true)

  @Test
  fun regularTier_250dp_showsCells() =
    assertCellsAtHeight(250, WidgetDensityTier.REGULAR, shown = true)

  @Test
  fun largeTier_300dp_showsCells() =
    assertCellsAtHeight(300, WidgetDensityTier.LARGE, shown = true)

  private fun assertCellsAtHeight(heightDp: Int, tier: WidgetDensityTier, shown: Boolean) {
    assertEquals(tier, WidgetLayoutSpec.fromSize(heightDp.toFloat(), CELLS.size).tier)
    render(overview(), heightDp = heightDp) {
      onNode(hasText("55%")).assertExists()
      // Every sample cell starts with "3.30" and no other text does.
      onAllNodes(hasText("3.30")).assertCountEquals(if (shown) CELLS.size else 0)
    }
  }

  private fun render(
    overview: BatteryOverview,
    widthDp: Int = 300,
    heightDp: Int = 300,
    assertions: GlanceAppWidgetUnitTest.() -> Unit,
  ) = runGlanceAppWidgetUnitTest {
    setContext(context)
    setAppWidgetSize(DpSize(widthDp.dp, heightDp.dp))
    val refresh = actionRunCallback<NoOpRefresh>()
    val openApp = actionStartActivity(openAppComponent)
    provideComposable { WidgetContent(overview, refresh, openApp) }
    assertions()
  }

  private fun overview(
    reading: BatteryReading? = sample(),
    error: SyncError? = null,
    refreshingSinceMillis: Long? = null,
    hasToken: Boolean = true,
  ): BatteryOverview =
    BatteryOverview.build(
      cache = CacheState(reading, error, refreshingSinceMillis),
      settings = configuredSettings(),
      hasToken = hasToken,
      nowMillis = NOW,
    )

  private fun sample(
    powerW: Double? = -207.0,
    currentA: Double? = -7.8,
    totalVoltageV: Double? = 26.5,
    cellVoltagesV: List<Double?> = CELLS,
  ) = BatteryReading(
    chargePercent = 54.6,
    powerW = powerW,
    currentA = currentA,
    totalVoltageV = totalVoltageV,
    cellVoltagesV = cellVoltagesV,
    updatedAtMillis = NOW - 60_000L,
  )

  class NoOpRefresh : ActionCallback {
    override suspend fun onAction(
      context: Context,
      glanceId: GlanceId,
      parameters: ActionParameters,
    ) = Unit
  }

  private companion object {
    const val NOW = 1_800_000_000_000L
    val CELLS = listOf(3.300, 3.301, 3.302, 3.303, 3.304, 3.305, 3.306, 3.307)
  }
}
