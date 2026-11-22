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
import androidx.glance.testing.unit.hasClickAction
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.GlanceNodeMatcher
import androidx.glance.testing.unit.MappedNode
import androidx.glance.text.EmittableText
import androidx.glance.testing.unit.hasContentDescription
import androidx.glance.testing.unit.hasContentDescriptionEqualTo
import androidx.glance.testing.unit.hasStartActivityClickAction
import androidx.glance.testing.unit.hasTestTag
import androidx.glance.testing.unit.hasAnyDescendant
import androidx.glance.layout.EmittableRow
import androidx.glance.testing.unit.hasText
import androidx.glance.testing.unit.hasTextEqualTo
import androidx.glance.Emittable
import androidx.glance.EmittableWithChildren
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
          formatters.updatedAt(UPDATED, NOW),
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

  /** The update time is the time on the drawing's day, else the date: the clock is not read. */
  @Test
  fun updateTime_followsTheDrawingsTime() {
    val nextDay = UPDATED + 24 * 3_600_000L
    render(model(now = UPDATED + 60_000L)) {
      onNode(hasTextEqualTo(formatters.updatedAt(UPDATED, UPDATED + 60_000L))).assertExists()
    }
    render(model(now = nextDay)) {
      onNode(hasTextEqualTo(formatters.updatedAt(UPDATED, nextDay))).assertExists()
    }
  }

  /** Spec 0005, 5.3: a red icon only, the reason in its content description, no error text. */
  @Test
  fun error_showsIconOnly() =
    LayoutSpec.all.forEach { spec ->
      render(model(error = SyncError.Unreachable), spec.bucketWidth, spec.bucketHeight) {
        onNode(hasContentDescriptionEqualTo("Sync error: Home Assistant is unreachable")).assertExists()
        onAllNodes(hasText(text(R.string.error_unreachable))).assertCountEquals(0)
        onAllNodes(hasTextEqualTo(formatters.updatedAt(UPDATED, NOW))).assertCountEquals(0)
        onNode(hasTextEqualTo("55")).assertExists()
      }
    }

  @Test
  fun refreshing_keepsButtonWithRefreshingDescription() {
    render(model()) {
      onNode(hasRunCallbackClickAction<NoOpRefresh>())
        .assert(hasContentDescription(WidgetTexts.refreshDescription(context, false, 1)))
    }
    render(model(refreshingSince = NOW - 1_000L)) {
      onNode(hasRunCallbackClickAction<NoOpRefresh>())
        .assert(hasContentDescription(WidgetTexts.refreshDescription(context, true, 1)))
    }
  }

  @Test
  fun refreshDescription_saysAge() {
    render(model(updated = NOW - 3 * 60_000L)) {
      onNode(hasRunCallbackClickAction<NoOpRefresh>())
        .assert(hasContentDescription("Refresh. Data is 3 minutes old"))
    }
    render(model(updated = NOW - 3 * 60_000L, refreshingSince = NOW - 1_000L)) {
      onNode(hasRunCallbackClickAction<NoOpRefresh>())
        .assert(hasContentDescription("Refreshing… Data is 3 minutes old"))
    }
  }

  /** With the timed sync off, data turns late after 20 min (spec 0016). */
  @Test
  fun dot_freshThenLate() {
    render(model(updated = NOW - 3 * 60_000L)) {
      onNode(hasTestTag("dot_fresh")).assertExists()
      onAllNodes(hasTestTag("dot_late")).assertCountEquals(0)
    }
    render(model(updated = NOW - 25 * 60_000L)) {
      onNode(hasTestTag("dot_late")).assertExists()
      onAllNodes(hasTestTag("dot_fresh")).assertCountEquals(0)
    }
  }

  @Test
  fun emptyStates_haveNoDot() =
    listOf(model(hasToken = false), model(reading = null)).forEach { empty ->
      render(empty) {
        onAllNodes(hasTestTag("dot_fresh")).assertCountEquals(0)
        onAllNodes(hasTestTag("dot_late")).assertCountEquals(0)
      }
    }

  @Test
  fun topRow_hasNoAutoToggle() =
    LayoutSpec.all.forEach { spec ->
      render(model(), spec.bucketWidth, spec.bucketHeight) {
        onAllNodes(hasContentDescriptionEqualTo("Auto refresh off")).assertCountEquals(0)
        onAllNodes(hasContentDescriptionEqualTo("Auto refresh on")).assertCountEquals(0)
        onNode(hasRunCallbackClickAction<NoOpRefresh>()).assertExists()
      }
    }

  @Test
  fun averageCellPill_iconInsteadOfLabel() =
    render(model()) {
      onAllNodes(hasContentDescriptionEqualTo(text(R.string.widget_average_cell)))
        .assertCountEquals(1)
      onNode(hasTextEqualTo(formatters.cellVoltage(3.312))).assertExists()
    }

  @Test
  fun missingAverageCell_showsDash() =
    render(model(sample.copy(averageCellVoltageV = null))) {
      onNode(hasTextEqualTo(text(R.string.value_missing))).assertExists()
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
      onAllNodes(hasStartActivityClickAction(openAppComponent)).assertCountEquals(2)
    }

  @Test
  fun emptyNoData_showsTheError() =
    render(model(reading = null, error = SyncError.Unauthorized)) {
      onNode(hasTextEqualTo(text(R.string.widget_no_data))).assertExists()
      onNode(hasTextEqualTo(text(R.string.error_unauthorized))).assertExists()
      onNode(hasTextEqualTo(text(R.string.widget_open_app))).assertExists()
    }

  /** The app's preview (spec 0006): every layout draws, nothing takes taps. */
  @Test
  fun noActions_eachBucket_nothingTakesTaps() =
    LayoutSpec.all.forEach { spec ->
      render(model(), spec.bucketWidth, spec.bucketHeight, withActions = false) {
        onNode(hasTestTag("layout_${spec.name}")).assertExists()
        onNode(hasTextEqualTo("55")).assertExists()
        onAllNodes(hasClickAction()).assertCountEquals(0)
      }
    }

  /** We are already in the app: the preview's empty state has no "Open app". */
  @Test
  fun noActions_empty_noOpenApp() =
    render(model(reading = null, error = SyncError.Unauthorized), withActions = false) {
      onNode(hasTextEqualTo(text(R.string.widget_no_data))).assertExists()
      onNode(hasTextEqualTo(text(R.string.error_unauthorized))).assertExists()
      onAllNodes(hasTextEqualTo(text(R.string.widget_open_app))).assertCountEquals(0)
      onAllNodes(hasClickAction()).assertCountEquals(0)
    }

  /** The preview's circles do nothing, so TalkBack skips them; the values stay described. */
  @Test
  fun noActions_eachBucket_buttonsAreNotDescribed() =
    LayoutSpec.all.forEach { spec ->
      render(model(), spec.bucketWidth, spec.bucketHeight, withActions = false) {
        onAllNodes(hasContentDescriptionEqualTo(WidgetTexts.refreshDescription(context, false, 1)))
          .assertCountEquals(0)
        onNode(hasContentDescriptionEqualTo(text(R.string.widget_balancing_off))).assertExists()
        onNode(hasTextEqualTo("55")).assertExists()
      }
    }

  @Test
  fun eachBucket_drawsItsLayout() =
    LayoutSpec.all.forEach { spec ->
      render(model(), spec.bucketWidth, spec.bucketHeight) {
        onNode(hasTestTag("layout_${spec.name}")).assertExists()
        onNode(hasTextEqualTo("55")).assertExists()
        onNode(hasTextEqualTo(formatters.voltage(26.5))).assertExists()
        onNode(hasTextEqualTo(formatters.temperature(27.0))).assertExists()
      }
    }

  /**
   * The home-screen scale changes sizes only: at 1.28 each bucket draws the same tree as at 1.0
   * (a scaled spec once missed the 5×2 and 4×3 layouts and drew 4×2's).
   */
  @Test
  fun hostScale_eachBucket_drawsItsLayout() =
    LayoutSpec.all.forEach { spec ->
      assertEquals(spec.name, shape(spec, 1f), shape(spec, 1.28f))
    }

  /** `WidgetContent(hostScale)` sizes the SOC number: bigger at 1.28 than at 1.0, smaller at 0.8. */
  @Test
  fun hostScale_makesTheSocNumberBigger() {
    assertTrue(socSize(1.28f) > socSize(1f))
    assertTrue(socSize(1f) > socSize(0.8f))
  }

  private fun socSize(hostScale: Float): Float {
    var size = 0f
    render(model(), hostScale = hostScale) {
      onNode(
          GlanceNodeMatcher<MappedNode>("SOC number") { node ->
            val text = node.value.emittable as? EmittableText
            (text?.text == "55").also { if (it) size = text!!.style?.fontSize?.value ?: 0f }
          }
        )
        .assertExists()
    }
    return size
  }

  @Test
  fun fiveByFour_stretchesFiveByThree() =
    render(model(), 340f, 278f) { onNode(hasTestTag("layout_5x3")).assertExists() }

  @Test
  fun belowMinimumSize_drawsFourByTwo() =
    render(model(), 240f, 110f) { onNode(hasTestTag("layout_4x2")).assertExists() }

  @Test
  fun grid_noSensor_mosfetAloneInRowTwo() =
    render(model(settings = settings(sensors = 0)), LayoutSpec.L4x3.bucketWidth, LayoutSpec.L4x3.bucketHeight) {
      val mosfet = hasContentDescriptionEqualTo(text(R.string.widget_mosfet_temperature))
      onNode(rowWithChildren(1) and hasAnyDescendant(mosfet)).assertExists()
      onAllNodes(rowWithChildren(3) and hasAnyDescendant(hasText("mV"))).assertCountEquals(1)
    }

  @Test
  fun grid_twoRowsOfTwo() =
    render(model(), LayoutSpec.L4x3.bucketWidth, LayoutSpec.L4x3.bucketHeight) {
      val mosfet = hasContentDescriptionEqualTo(text(R.string.widget_mosfet_temperature))
      val battery = hasContentDescriptionEqualTo(text(R.string.widget_battery_temperature))
      onNode(rowWithChildren(3) and hasAnyDescendant(mosfet) and hasAnyDescendant(battery)).assertExists()
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

  /** The tree of the layout drawn at [spec]'s bucket: element types only, no sizes. */
  private fun shape(spec: LayoutSpec, hostScale: Float): String {
    var shape = ""
    val tag = hasTestTag("layout_${spec.name}")
    render(model(), spec.bucketWidth, spec.bucketHeight, hostScale = hostScale) {
      onNode(
          GlanceNodeMatcher<MappedNode>("layout ${spec.name}") { node ->
            tag.matches(node).also { if (it) shape = shapeOf(node.value.emittable) }
          }
        )
        .assertExists()
    }
    return shape
  }

  private fun shapeOf(e: Emittable): String =
    e.javaClass.simpleName +
      ((e as? EmittableWithChildren)?.children?.joinToString(",", "(", ")", transform = ::shapeOf) ?: "")

  /** A Row with [n] children (pills and the spacers between them). */
  private fun rowWithChildren(n: Int) =
    GlanceNodeMatcher<MappedNode>("row with $n children") { node ->
      (node.value.emittable as? EmittableRow)?.children?.size == n
    }

  private fun hasTextColor(expected: Color) =
    GlanceNodeMatcher<MappedNode>("has text colour $expected") { node ->
      (node.value.emittable as? EmittableText)?.style?.color?.getColor(context) == expected
    }

  private fun render(
    model: WidgetModel,
    widthDp: Float = LayoutSpec.L4x2.bucketWidth,
    heightDp: Float = LayoutSpec.L4x2.bucketHeight,
    fontPercents: FontPercents = FontPercents(),
    colors: WidgetColors = WidgetColors(ThemeChoice.SYSTEM),
    hostScale: Float = 1f,
    withActions: Boolean = true,
    assertions: GlanceAppWidgetUnitTest.() -> Unit,
  ) = runGlanceAppWidgetUnitTest {
    setContext(context)
    setAppWidgetSize(DpSize(widthDp.dp, heightDp.dp))
    val actions =
      WidgetActions(
          openApp = actionStartActivity(openAppComponent),
          refresh = actionRunCallback<NoOpRefresh>(),
        )
        .takeIf { withActions }
    provideComposable { WidgetContent(model, fontPercents, colors, actions, hostScale, FakeMeasurer) }
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
    updated: Long = UPDATED,
    now: Long = NOW,
  ): WidgetModel =
    WidgetModel.build(
      BatteryOverview.build(
        CacheState(reading?.copy(updatedAtMillis = updated), error, refreshingSince),
        settings,
        hasToken,
        now,
      ),
      settings,
    )

  class NoOpRefresh : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) =
      Unit
  }

  private companion object {
    const val NOW = 1_800_000_000_000L
    const val UPDATED = NOW - 60_000L
  }
}
