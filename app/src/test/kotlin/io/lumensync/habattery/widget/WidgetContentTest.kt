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
import androidx.glance.testing.unit.hasContentDescription
import androidx.glance.testing.unit.hasStartActivityClickAction
import androidx.glance.testing.unit.hasText
import androidx.glance.testing.unit.hasTextEqualTo
import androidx.test.core.app.ApplicationProvider
import io.lumensync.habattery.R
import io.lumensync.habattery.domain.BatteryOverview
import io.lumensync.habattery.domain.model.BatteryReading
import io.lumensync.habattery.domain.model.BatteryState
import io.lumensync.habattery.domain.model.CacheState
import io.lumensync.habattery.domain.model.Settings
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
        .assert(hasContentDescription(context.getString(R.string.widget_refresh_action)))
    }

  @Test
  fun normal_showsChargeAndDelta() =
    render(overview()) {
      onNode(hasText("55%")).assertExists()
      onNode(hasTextEqualTo(context.getString(R.string.delta_label, formatters.deltaMv(12))))
        .assertExists()
      assertPowerOnlyInStatus()
      onNode(hasTextEqualTo(formatters.current(-7.8))).assertExists()
      onNode(hasTextEqualTo(formatters.voltage(26.5))).assertExists()
      onNode(hasRunCallbackClickAction<NoOpRefresh>())
        .assert(hasAnyDescendant(hasText(context.getString(R.string.widget_refresh_symbol))))
        .assert(hasContentDescription(context.getString(R.string.widget_refresh_action)))
    }

  @Test
  fun noDelta_hidesDeltaText() =
    render(overview(sample(deltaCellVoltageV = null))) {
      onAllNodes(hasText("mV")).assertCountEquals(0)
    }

  @Test
  fun noCellText_isShown() =
    render(overview()) { onAllNodes(hasText("3.30")).assertCountEquals(0) }

  @Test
  fun summaryTier_110dp_showsCharge() =
    render(overview(), heightDp = 110) { onNode(hasText("55%")).assertExists() }

  @Test
  fun missingPower_hidesPowerLine() =
    render(overview(sample(powerW = null))) {
      onAllNodes(hasText(" W")).assertCountEquals(0)
    }

  @Test
  fun missingCurrentAndVoltage_hideTheirLines() =
    render(overview(sample(currentA = null, totalVoltageV = null))) {
      onNode(hasTextEqualTo(context.getString(R.string.status_unknown))).assertExists()
      onAllNodes(hasTextEqualTo(formatters.current(-7.8))).assertCountEquals(0)
      onAllNodes(hasTextEqualTo(formatters.voltage(26.5))).assertCountEquals(0)
    }

  @Test
  fun error_showsErrorLine() =
    render(overview(error = SyncError.Unreachable)) {
      onNode(hasText(context.getString(R.string.error_unreachable))).assertExists()
      onNode(hasText("55%")).assertExists()
    }

  @Test
  fun refreshing_keepsSymbolWithRefreshingDescription() =
    listOf(sample(), null)
      .map { overview(reading = it, refreshingSinceMillis = NOW - 1_000L) }
      .forEach { refreshing ->
        render(refreshing) {
          // The symbol stays (no wider text label that cuts the metrics); only its description changes.
          onNode(hasRunCallbackClickAction<NoOpRefresh>())
            .assert(hasAnyDescendant(hasTextEqualTo(context.getString(R.string.widget_refresh_symbol))))
            .assert(hasContentDescription(context.getString(R.string.widget_refreshing)))
          onAllNodes(hasText(context.getString(R.string.widget_refreshing))).assertCountEquals(0)
        }
      }

  @Test
  @Config(qualifiers = "uk")
  fun ukrainianCompact_statusKeepsTimeLeft() {
    val overview = overview(settings = configuredSettings().copy(capacityAh = 314.0))
    val time = checkNotNull(overview.timeRemaining)
    assertEquals(WidgetDensityTier.COMPACT, WidgetLayoutSpec.fromSize(160f).tier)
    render(overview, widthDp = 230, heightDp = 160) {
      onNode(hasTextEqualTo(formatters.status(BatteryState.DISCHARGING, -207.0, time)))
        .assert(hasText(formatters.duration(time.duration)))
    }
  }

  @Test
  fun statusLine_wrapsToTwoLinesBelowSummaryTier() =
    WidgetDensityTier.entries.forEach { tier ->
      val expected = if (tier == WidgetDensityTier.SUMMARY_ONLY) 1 else 2
      listOf(true, false).forEach { narrow ->
        assertEquals("$tier", expected, WidgetDimens.of(tier, narrow).statusLines)
      }
    }

  @Test
  fun everyTier_showsChargeAndDelta() =
    listOf(110 to WidgetDensityTier.SUMMARY_ONLY, 160 to WidgetDensityTier.COMPACT, 250 to WidgetDensityTier.REGULAR)
      .forEach { (heightDp, tier) ->
        assertEquals(tier, WidgetLayoutSpec.fromSize(heightDp.toFloat()).tier)
        render(overview(), heightDp = heightDp) {
          onNode(hasText("55%")).assertExists()
          onNode(hasText("12 mV")).assertExists()
        }
      }

  /** The sample's power shows exactly once: inside the status line, not in the metrics row. */
  private fun GlanceAppWidgetUnitTest.assertPowerOnlyInStatus() {
    val power = formatters.power(-207.0)
    onNode(hasText(power))
      .assert(hasTextEqualTo(context.getString(R.string.status_discharging, power)))
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
    settings: Settings = configuredSettings(),
  ): BatteryOverview =
    BatteryOverview.build(
      cache = CacheState(reading, error, refreshingSinceMillis),
      settings = settings,
      hasToken = hasToken,
      nowMillis = NOW,
    )

  private fun sample(
    powerW: Double? = -207.0,
    currentA: Double? = -7.8,
    totalVoltageV: Double? = 26.5,
    deltaCellVoltageV: Double? = 0.012,
  ) = BatteryReading(
    chargePercent = 54.6,
    powerW = powerW,
    currentA = currentA,
    totalVoltageV = totalVoltageV,
    deltaCellVoltageV = deltaCellVoltageV,
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
  }
}
