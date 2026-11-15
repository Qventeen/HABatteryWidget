package io.lumensync.habattery.widget

import android.content.ComponentName
import android.content.Context
import androidx.compose.ui.graphics.Color
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
import androidx.glance.testing.GlanceNodeMatcher
import androidx.glance.testing.unit.MappedNode
import androidx.glance.text.EmittableText
import androidx.glance.testing.unit.hasContentDescription
import androidx.glance.testing.unit.hasContentDescriptionEqualTo
import androidx.glance.testing.unit.hasStartActivityClickAction
import androidx.glance.testing.unit.hasText
import androidx.glance.testing.unit.hasTextEqualTo
import androidx.test.core.app.ApplicationProvider
import io.lumensync.habattery.R
import io.lumensync.habattery.domain.BatteryOverview
import io.lumensync.habattery.domain.WidgetModel
import io.lumensync.habattery.domain.model.BatteryReading
import io.lumensync.habattery.domain.model.CacheState
import io.lumensync.habattery.domain.model.DisplaySettings
import io.lumensync.habattery.domain.model.FontPercents
import io.lumensync.habattery.domain.model.ThemeChoice
import io.lumensync.habattery.domain.model.MainValue
import io.lumensync.habattery.domain.model.RefreshSettings
import io.lumensync.habattery.domain.model.Settings
import io.lumensync.habattery.domain.model.SyncError
import io.lumensync.habattery.domain.model.UnderSoc
import io.lumensync.habattery.format.Formatters
import io.lumensync.habattery.testing.configuredSettings
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

  private fun text(id: Int, vararg args: Any): String = context.getString(id, *args)

  private val openAppComponent = ComponentName("io.lumensync.habattery", "OpenApp")

  @Test
  fun normal_showsValues() =
    render(model()) {
      listOf(
          "55",
          formatters.kilowattHours(4.1605),
          formatters.voltage(26.5),
          formatters.current(10.0),
          formatters.cellVoltage(3.312),
          text(R.string.delta_label, formatters.deltaMv(12)),
          formatters.temperature(25.0),
          formatters.temperature(27.0),
          formatters.updatedAt(UPDATED),
          formatters.duration(sampleTimeLeft()),
        )
        .forEach { onNode(hasTextEqualTo(it)).assertExists() }
      onNode(hasTextEqualTo(text(R.string.widget_soc_label))).assertExists()
    }

  @Test
  fun ahAndPower_replaceKwhAndCurrent() =
    render(model(settings = settings(display = DisplaySettings(UnderSoc.AH, MainValue.POWER)))) {
      onNode(hasTextEqualTo(formatters.ampHours(157.0))).assertExists()
      onNode(hasTextEqualTo(formatters.power(265.0))).assertExists()
      onAllNodes(hasText("kWh")).assertCountEquals(0)
      onAllNodes(hasTextEqualTo(formatters.current(10.0))).assertCountEquals(0)
    }

  @Test
  fun discharging_arrowIsDescribed() =
    render(model()) {
      onNode(hasContentDescriptionEqualTo(text(R.string.widget_discharging))).assertExists()
      onAllNodes(hasContentDescriptionEqualTo(text(R.string.widget_charging))).assertCountEquals(0)
    }

  @Test
  fun oneSensor_batteryAndMosfetPills() =
    render(model()) {
      onNode(hasContentDescriptionEqualTo(text(R.string.widget_battery_temperature))).assertExists()
      onNode(hasContentDescriptionEqualTo(text(R.string.widget_mosfet_temperature))).assertExists()
      onAllNodes(hasContentDescriptionEqualTo(text(R.string.widget_average_temperature)))
        .assertCountEquals(0)
    }

  @Test
  fun twoSensors_oneAveragePill() =
    render(model(sample.copy(temperaturesC = listOf(44.0, 47.0)), settings(sensors = 2))) {
      onNode(hasContentDescriptionEqualTo(text(R.string.widget_average_temperature))).assertExists()
      onNode(hasTextEqualTo(formatters.temperature(45.5))).assertExists()
      onAllNodes(hasContentDescriptionEqualTo(text(R.string.widget_battery_temperature)))
        .assertCountEquals(0)
      onNode(hasContentDescriptionEqualTo(text(R.string.widget_mosfet_temperature))).assertExists()
    }

  @Test
  fun noSensor_onlyMosfetPill() =
    render(model(settings = settings(sensors = 0))) {
      onAllNodes(hasContentDescriptionEqualTo(text(R.string.widget_battery_temperature)))
        .assertCountEquals(0)
      onAllNodes(hasContentDescriptionEqualTo(text(R.string.widget_average_temperature)))
        .assertCountEquals(0)
      onNode(hasContentDescriptionEqualTo(text(R.string.widget_mosfet_temperature))).assertExists()
    }

  @Test
  fun error_replacesUpdateTime() =
    render(model(error = SyncError.Unreachable)) {
      onNode(hasTextEqualTo(text(R.string.error_unreachable))).assertExists()
      onNode(hasContentDescriptionEqualTo(text(R.string.widget_sync_error))).assertExists()
      onAllNodes(hasTextEqualTo(formatters.updatedAt(UPDATED))).assertCountEquals(0)
      onNode(hasTextEqualTo("55")).assertExists()
    }

  @Test
  fun refreshing_keepsButtonWithRefreshingDescription() {
    render(model()) {
      onNode(hasRunCallbackClickAction<NoOpRefresh>())
        .assert(hasContentDescription(text(R.string.widget_refresh_action)))
    }
    render(model(refreshingSince = NOW - 1_000L)) {
      onNode(hasRunCallbackClickAction<NoOpRefresh>())
        .assert(hasContentDescription(text(R.string.widget_refreshing)))
    }
  }

  @Test
  fun autoToggle_describesItsState() {
    render(model()) {
      onNode(hasRunCallbackClickAction<NoOpToggle>())
        .assert(hasContentDescription(text(R.string.widget_auto_off)))
    }
    render(model(settings = settings(refresh = RefreshSettings(true, 30)))) {
      onNode(hasRunCallbackClickAction<NoOpToggle>())
        .assert(hasContentDescription(text(R.string.widget_auto_on)))
    }
  }

  @Test
  fun missingDelta_showsDash() =
    render(model(sample.copy(deltaCellVoltageV = null))) {
      onNode(hasTextEqualTo(text(R.string.delta_label, text(R.string.value_missing)))).assertExists()
    }

  @Test
  fun missingVoltage_showsDash() =
    render(model(sample.copy(totalVoltageV = null))) {
      // The voltage pill, and the kWh line under SOC (kWh needs the voltage).
      onAllNodes(hasTextEqualTo(text(R.string.value_missing))).assertCountEquals(2)
    }

  @Test
  fun balancing_isDescribed() {
    render(model()) {
      onNode(hasContentDescriptionEqualTo(text(R.string.widget_balancing_off))).assertExists()
    }
    render(model(sample.copy(balancing = null))) {
      onNode(hasContentDescriptionEqualTo(text(R.string.widget_balancing_unknown))).assertExists()
    }
  }

  @Test
  fun ring_isDescribed() =
    render(model()) {
      onNode(hasContentDescriptionEqualTo(context.resources.getQuantityString(R.plurals.widget_soc_description, 55, 55))).assertExists()
    }

  @Test
  fun emptySetup_offersOpenAppOnly() =
    render(model(hasToken = false)) {
      onNode(hasTextEqualTo(text(R.string.error_not_configured))).assertExists()
      onNode(hasTextEqualTo(text(R.string.widget_open_app))).assertExists()
      onAllNodes(hasRunCallbackClickAction<NoOpRefresh>()).assertCountEquals(0)
      onAllNodes(hasRunCallbackClickAction<NoOpToggle>()).assertCountEquals(0)
      onAllNodes(hasStartActivityClickAction(openAppComponent)).assertCountEquals(2)
    }

  @Test
  fun emptyNoData_showsTheError() =
    render(model(reading = null, error = SyncError.Unauthorized)) {
      onNode(hasTextEqualTo(text(R.string.widget_no_data))).assertExists()
      onNode(hasTextEqualTo(text(R.string.error_unauthorized))).assertExists()
      onNode(hasTextEqualTo(text(R.string.widget_open_app))).assertExists()
    }

  @Test
  fun belowMinimumSize_stillRendersEverything() =
    render(model(), widthDp = 276, heightDp = 140) {
      onNode(hasTextEqualTo("55")).assertExists()
      onNode(hasTextEqualTo(formatters.voltage(26.5))).assertExists()
      onNode(hasTextEqualTo(formatters.temperature(27.0))).assertExists()
    }

  @Test
  fun largeFonts_stillRender() =
    render(model(), fontPercents = FontPercents(soc = 100, underSoc = 100, main = 100, cell = 100, small = 100)) {
      onNode(hasTextEqualTo("55")).assertExists()
    }

  /** The chosen theme reaches the drawing: the SOC number takes the palette's text colour. */
  @Test
  @Config(qualifiers = "en-notnight")
  fun darkTheme_drawsDarkColoursByDay() =
    render(model(), colors = WidgetColors(ThemeChoice.DARK)) {
      onNode(hasTextEqualTo("55") and hasTextColor(WidgetPalette.Dark.text)).assertExists()
    }

  @Test
  @Config(qualifiers = "en-notnight")
  fun systemTheme_drawsLightColoursByDay() =
    render(model(), colors = WidgetColors(ThemeChoice.SYSTEM)) {
      onNode(hasTextEqualTo("55") and hasTextColor(WidgetPalette.Light.text)).assertExists()
    }

  private fun hasTextColor(expected: Color) =
    GlanceNodeMatcher<MappedNode>("has text colour $expected") { node ->
      (node.value.emittable as? EmittableText)?.style?.color?.getColor(context) == expected
    }

  private fun render(
    model: WidgetModel,
    widthDp: Int = 360,
    heightDp: Int = 180,
    fontPercents: FontPercents = FontPercents(),
    colors: WidgetColors = WidgetColors(ThemeChoice.SYSTEM),
    assertions: GlanceAppWidgetUnitTest.() -> Unit,
  ) = runGlanceAppWidgetUnitTest {
    setContext(context)
    setAppWidgetSize(DpSize(widthDp.dp, heightDp.dp))
    val actions =
      WidgetActions(
        openApp = actionStartActivity(openAppComponent),
        refresh = actionRunCallback<NoOpRefresh>(),
        toggleAuto = actionRunCallback<NoOpToggle>(),
      )
    provideComposable { WidgetContent(model, fontPercents, colors, actions) }
    assertions()
  }

  private val sample =
    BatteryReading(
      chargePercent = 54.6,
      powerW = -265.0,
      currentA = -10.0,
      totalVoltageV = 26.5,
      capacityRemainingAh = 157.0,
      averageCellVoltageV = 3.312,
      deltaCellVoltageV = 0.012,
      mosfetTemperatureC = 27.0,
      temperaturesC = listOf(25.0),
      balancing = false,
      updatedAtMillis = UPDATED,
    )

  /** 157 Ah at 10 A. */
  private fun sampleTimeLeft() = kotlin.time.Duration.parse("15h 42m")

  private fun settings(
    sensors: Int = 1,
    display: DisplaySettings = DisplaySettings(),
    refresh: RefreshSettings = RefreshSettings(),
  ): Settings {
    val base = configuredSettings()
    return base.copy(
      entities = base.entities.copy(temperatures = List(sensors) { "sensor.t$it" }),
      display = display,
      refresh = refresh,
    )
  }

  private fun model(
    reading: BatteryReading? = sample,
    settings: Settings = settings(),
    error: SyncError? = null,
    hasToken: Boolean = true,
    refreshingSince: Long? = null,
  ): WidgetModel =
    WidgetModel.build(
      BatteryOverview.build(CacheState(reading, error, refreshingSince), settings, hasToken, NOW),
      settings,
    )

  class NoOpRefresh : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) =
      Unit
  }

  class NoOpToggle : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) =
      Unit
  }

  private companion object {
    const val NOW = 1_800_000_000_000L
    const val UPDATED = NOW - 60_000L
  }
}
