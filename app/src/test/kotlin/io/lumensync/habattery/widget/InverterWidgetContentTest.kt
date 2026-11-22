package io.lumensync.habattery.widget

import android.content.ComponentName
import android.content.Context
import android.view.View
import android.view.View.MeasureSpec
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.DrawableRes
import androidx.glance.AndroidResourceImageProvider
import androidx.glance.EmittableImage
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Visibility
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.testing.unit.GlanceAppWidgetUnitTest
import androidx.glance.appwidget.testing.unit.hasRunCallbackClickAction
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.layout.EmittableColumn
import androidx.glance.layout.EmittableRow
import androidx.glance.testing.GlanceNodeMatcher
import androidx.glance.testing.unit.MappedNode
import androidx.glance.testing.unit.hasClickAction
import androidx.glance.testing.unit.hasContentDescription
import androidx.glance.testing.unit.hasContentDescriptionEqualTo
import androidx.glance.testing.unit.hasStartActivityClickAction
import androidx.glance.testing.unit.hasTestTag
import androidx.glance.testing.unit.hasTextEqualTo
import androidx.core.view.children
import androidx.glance.visibility
import androidx.test.core.app.ApplicationProvider
import io.lumensync.habattery.MainActivity
import io.lumensync.habattery.R
import io.lumensync.habattery.domain.InverterWidgetModel
import io.lumensync.habattery.domain.model.CacheState
import io.lumensync.habattery.domain.model.InverterFontPercents
import io.lumensync.habattery.domain.model.InverterReading
import io.lumensync.habattery.domain.model.ModeStyle
import io.lumensync.habattery.domain.model.Settings
import io.lumensync.habattery.domain.model.SyncError
import io.lumensync.habattery.domain.model.ThemeChoice
import io.lumensync.habattery.format.Formatters
import io.lumensync.habattery.testing.configuredSettings
import kotlin.math.roundToInt
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en")
class InverterWidgetContentTest {
  private val context: Context
    get() = ApplicationProvider.getApplicationContext()

  private val formatters: Formatters
    get() = Formatters(context)

  private fun text(id: Int, vararg args: Any): String = context.getString(id, *args)

  private val openAppComponent = ComponentName("io.lumensync.habattery", "OpenApp")

  private val settings = configuredSettings(battery = false, inverter = true)

  private val iconWord = settings.copy(inverter = settings.inverter.copy(modeStyle = ModeStyle.ICON_WORD))

  /** Big icon: the mode is the icon alone, described by its word. */
  @Test
  fun offGrid_bigIcon_4x1() =
    render(model(), InverterLayoutSpec.S4X1) {
      val word = text(R.string.mode_word_off_grid)
      val icon = hasImage(R.drawable.ic_inv_battery_charging_full)
      onNode(hasContentDescriptionEqualTo(word) and icon).assertExists()
      onAllNodes(hasTextEqualTo(word)).assertCountEquals(0)
      onNode(hasTextEqualTo(formatters.wholeVolts(0.0))).assertExists()
      onNode(hasTextEqualTo(formatters.wholeVolts(230.0))).assertExists()
      onNode(hasChild(hasImage(R.drawable.ic_alert))).assert(isGone())
      onAllNodes(hasContentDescription("Sync error")).assertCountEquals(0)
    }

  @Test
  fun iconWord_3x1_wordUnderIcon() =
    render(model(settings = iconWord), InverterLayoutSpec.S3X1) {
      val word = hasTextEqualTo(text(R.string.mode_word_off_grid))
      val icon = hasImage(R.drawable.ic_inv_battery_charging_full)
      onNode(isColumn() and hasChild(icon) and hasChild(word)).assertExists()
    }

  @Test
  fun iconWord_4x1_wordBesideIcon() =
    render(model(settings = iconWord), InverterLayoutSpec.S4X1) {
      val word = hasTextEqualTo(text(R.string.mode_word_off_grid))
      val icon = hasImage(R.drawable.ic_inv_battery_charging_full)
      onNode(isRow() and hasChild(icon) and hasChild(word)).assertExists()
      onAllNodes(isColumn() and hasChild(word)).assertCountEquals(0)
    }

  /** A text the mode table does not know: the "other" icon and the Home Assistant text as it is. */
  @Test
  fun unknownMode_iconWord_showsTheTextAsItIs() =
    render(model(cache(mode = "Eco"), iconWord), InverterLayoutSpec.S4X1) {
      onNode(hasTextEqualTo("Eco")).assertExists()
      onNode(hasImage(R.drawable.ic_inv_help)).assertExists()
    }

  @Test
  fun data_hasRefreshWithDot() =
    InverterLayoutSpec.entries.forEach { spec ->
      render(model(), spec) {
        onNode(hasRunCallbackClickAction<NoOpRefresh>())
          .assert(hasContentDescriptionEqualTo(WidgetTexts.refreshDescription(context, false, 1)))
        onNode(hasTestTag("dot_fresh")).assertExists()
      }
    }

  /** The last values stay; a red icon above ↻ tells the reason, and ↻ still refreshes. */
  @Test
  fun error_showsAlertAboveRefresh() =
    InverterLayoutSpec.entries.forEach { spec ->
      render(model(cache(error = SyncError.Unreachable)), spec) {
        val alert =
          hasContentDescriptionEqualTo("Sync error: Home Assistant is unreachable") and
            hasChild(hasImage(R.drawable.ic_alert))
        val refresh = hasRunCallbackClickAction<NoOpRefresh>()
        onNode(alert).assertExists()
        onNode(refresh).assertExists()
        onAllNodes(hasImage(R.drawable.ic_alert)).assertCountEquals(1)
        onNode(isColumn() and hasDescendant(alert) and hasDescendant(refresh)).assertExists()
        onNode(hasTextEqualTo(formatters.wholeVolts(230.0))).assertExists()
      }
    }

  /**
   * ↻ stays centred on the card, in line with the pills, when the home screen draws the card lower
   * or higher than the design's 59 dp. Its tap area spans the column's width; the ⚠ sits above it,
   * outside the tap area, from 2.5 dp below the card's top at 59 dp.
   */
  @Test
  fun refresh_centredOnTheCard_atEveryHeight() =
    listOf(53f, 59f, 70f).forEach { height ->
      val spec = InverterLayoutSpec.S3X1
      val card = layOut(model(cache(error = SyncError.Unreachable)), spec, height)
      val refresh = card.find(WidgetTexts.refreshDescription(context, false, 1))!!
      val alert = card.find("Sync error: Home Assistant is unreachable")!!
      val at = "at $height dp"
      assertEquals(at, card.height / 2f, refresh.topIn(card) + refresh.height / 2f, 1f)
      assertEquals(at, spec.design.refresh + spec.design.padding, refresh.width.toFloat(), 1f)
      assertTrue(at, alert.topIn(card) + alert.height <= refresh.topIn(card))
      if (height == 59f) assertEquals(2.5f, alert.topIn(card).toFloat(), 1f)
    }

  /** While a sync runs ↻ stays and says so, with the data's age. */
  @Test
  fun refreshing_describedWithAge() =
    render(model(cache(updated = NOW - 3 * 60_000L).copy(refreshingSinceMillis = NOW - 1_000L))) {
      onNode(hasRunCallbackClickAction<NoOpRefresh>())
        .assert(hasContentDescriptionEqualTo("Refreshing… Data is 3 minutes old"))
    }

  @Test
  fun empty_hasNoRefresh() =
    listOf(model(settings = configuredSettings()), model(CacheState())).forEach { empty ->
      render(empty) {
        onAllNodes(hasRunCallbackClickAction<NoOpRefresh>()).assertCountEquals(0)
        onAllNodes(hasTestTag("dot_fresh")).assertCountEquals(0)
        onAllNodes(hasTestTag("dot_late")).assertCountEquals(0)
      }
    }

  /** The app's preview draws ↻ and its dot, but only the card would take taps. */
  @Test
  fun preview_noRefreshAction() =
    render(model(), refresh = false) {
      onNode(hasImage(R.drawable.ic_refresh)).assertExists()
      onNode(hasTestTag("dot_fresh")).assertExists()
      onAllNodes(hasClickAction()).assertCountEquals(1)
      onAllNodes(hasContentDescription(WidgetTexts.refreshDescription(context, false, 1))).assertCountEquals(0)
    }

  @Test
  fun setup_showsSetupText() =
    render(model(settings = configuredSettings())) {
      onNode(hasTextEqualTo(text(R.string.inverter_setup))).assertExists()
      onNode(hasImage(R.drawable.ic_inv_tower)).assertExists()
      onAllNodes(hasTextEqualTo(text(R.string.widget_open_app))).assertCountEquals(0)
      // The card is the only tap target: no button.
      onAllNodes(hasStartActivityClickAction(openAppComponent)).assertCountEquals(1)
    }

  @Test
  fun noReading_showsNoDataText() =
    render(model(CacheState(inverterError = SyncError.Unreachable))) {
      onNode(hasTextEqualTo(text(R.string.inverter_no_data))).assertExists()
      onNode(hasImage(R.drawable.ic_inv_tower)).assertExists()
    }

  @Test
  fun noVolts_showsDash() =
    render(model(cache(grid = null, output = null))) {
      onAllNodes(hasTextEqualTo(text(R.string.value_missing))).assertCountEquals(2)
    }

  /** The app's preview: nothing takes taps. */
  @Test
  fun noAction_nothingTakesTaps() =
    render(model(), openApp = false, refresh = false) { onAllNodes(hasClickAction()).assertCountEquals(0) }

  /** The app opens on the tapped widget: the tap carries the widget's id. */
  @Test
  fun tap_carriesTheTappedWidgetId() =
    runGlanceAppWidgetUnitTest {
      setContext(context)
      setAppWidgetSize(InverterLayoutSpec.S4X1.bucket)
      provideComposable { InverterStrip(cache(), settings, hasToken = true, NOW, appWidgetId = 42, FakeMeasurer) }
      val tap = actionParametersOf(TappedWidgetKey to 42)
      onNode(hasStartActivityClickAction<MainActivity>(tap)).assertExists()
    }

  /** The widget draws the strip made for its bucket. */
  @Test
  fun eachBucket_drawsItsStrip() =
    InverterLayoutSpec.entries.forEach { spec ->
      runGlanceAppWidgetUnitTest {
        setContext(context)
        setAppWidgetSize(spec.bucket)
        provideComposable { InverterStrip(cache(), settings, hasToken = true, NOW, appWidgetId = 1, FakeMeasurer) }
        onNode(hasTestTag("strip_${spec.name}")).assertExists()
      }
    }

  private fun hasImage(@DrawableRes id: Int) =
    GlanceNodeMatcher<MappedNode>("shows drawable $id") { node ->
      ((node.value.emittable as? EmittableImage)?.provider as? AndroidResourceImageProvider)?.resId == id
    }

  private fun isGone() =
    GlanceNodeMatcher<MappedNode>("is gone") { node -> node.value.emittable.modifier.any { it == GONE } }

  private fun isRow() = GlanceNodeMatcher<MappedNode>("is a Row") { it.value.emittable is EmittableRow }

  private fun isColumn() =
    GlanceNodeMatcher<MappedNode>("is a Column") { it.value.emittable is EmittableColumn }

  private fun hasChild(matcher: GlanceNodeMatcher<MappedNode>) =
    GlanceNodeMatcher<MappedNode>("has a matching child") { node -> node.children().any(matcher::matches) }

  private fun hasDescendant(matcher: GlanceNodeMatcher<MappedNode>): GlanceNodeMatcher<MappedNode> =
    GlanceNodeMatcher("has a matching descendant") { node ->
      node.children().any { matcher.matches(it) || hasDescendant(matcher).matches(it) }
    }

  /** The strip as the home screen shows it: its views laid out at [heightDp] (density 1 here). */
  private fun layOut(model: InverterWidgetModel, spec: InverterLayoutSpec, heightDp: Float): View {
    val widget =
      PreviewWidget {
        InverterWidgetContent(
          model,
          spec,
          WidgetColors(ThemeChoice.SYSTEM),
          InverterFontPercents(),
          hostScale = 1f,
          openApp = actionStartActivity(openAppComponent),
          refresh = actionRunCallback<NoOpRefresh>(),
          measurer = FakeMeasurer,
        )
      }
    val views = runBlocking { widget.views(context, spec.bucket) }!!
    val card = views.apply(context, FrameLayout(context))
    val w = spec.bucketWidthDp.roundToInt()
    val h = heightDp.roundToInt()
    card.measure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY))
    card.layout(0, 0, w, h)
    return card
  }

  private fun View.find(description: String): View? =
    if (contentDescription == description) this
    else (this as? ViewGroup)?.children?.firstNotNullOfOrNull { it.find(description) }

  private fun View.topIn(root: View): Int {
    var view = this
    var top = 0
    while (view !== root) {
      top += view.top
      view = view.parent as View
    }
    return top
  }

  private fun render(
    model: InverterWidgetModel,
    spec: InverterLayoutSpec = InverterLayoutSpec.S4X1,
    openApp: Boolean = true,
    refresh: Boolean = true,
    assertions: GlanceAppWidgetUnitTest.() -> Unit,
  ) = runGlanceAppWidgetUnitTest {
    setContext(context)
    setAppWidgetSize(spec.bucket)
    provideComposable {
      InverterWidgetContent(
        model,
        spec,
        WidgetColors(ThemeChoice.SYSTEM),
        InverterFontPercents(),
        hostScale = 1f,
        openApp = actionStartActivity(openAppComponent).takeIf { openApp },
        refresh = actionRunCallback<NoOpRefresh>().takeIf { refresh },
        measurer = FakeMeasurer,
      )
    }
    assertions()
  }

  private fun cache(
    mode: String? = "Off-Grid",
    grid: Double? = 0.0,
    output: Double? = 230.0,
    error: SyncError? = null,
    updated: Long = NOW - 60_000L,
  ) =
    CacheState(
      inverterReading = InverterReading(mode, grid, output, updatedAtMillis = updated),
      inverterError = error,
    )

  private fun model(cache: CacheState = cache(), settings: Settings = this.settings) =
    InverterWidgetModel.from(cache, settings, hasToken = true, NOW)

  class NoOpRefresh : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) =
      Unit
  }

  private companion object {
    const val NOW = 1_800_000_000_000L

    /** Equal to the element `visibility(Gone)` adds; its class is Glance-internal. */
    val GONE = GlanceModifier.visibility(Visibility.Gone)
  }
}
