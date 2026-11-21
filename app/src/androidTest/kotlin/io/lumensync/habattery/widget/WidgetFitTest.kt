package io.lumensync.habattery.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.compose
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.lumensync.habattery.MainActivity
import io.lumensync.habattery.domain.BatteryOverview
import io.lumensync.habattery.domain.WidgetModel
import io.lumensync.habattery.domain.model.BatteryReading
import io.lumensync.habattery.domain.model.CacheState
import io.lumensync.habattery.domain.model.ConnectionSettings
import io.lumensync.habattery.domain.model.DisplaySettings
import io.lumensync.habattery.domain.model.EntitySettings
import io.lumensync.habattery.domain.model.FontGroup
import io.lumensync.habattery.domain.model.FontPercents
import io.lumensync.habattery.domain.model.MainValue
import io.lumensync.habattery.domain.model.RefreshSettings
import io.lumensync.habattery.domain.model.Settings
import io.lumensync.habattery.domain.model.SyncError
import io.lumensync.habattery.domain.model.ThemeChoice
import io.lumensync.habattery.domain.model.UnderSoc
import java.io.File
import kotlin.math.roundToInt
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The fit check of spec 0005 (§6): the real widget composed by Glance at each bucket, laid out at
 * the bucket and at the owner's sizes, with worst-case and typical values, every font group at 10,
 * 50 and 100 %, in English and Ukrainian. Every text must show whole: one line, as wide as it
 * needs, inside every view that clips it. With `-e fitShots true` the drawings are saved to the
 * app's files (`fit/`) for comparing with the design PNGs.
 */
@RunWith(AndroidJUnit4::class)
class WidgetFitTest {
  private val target: Context = InstrumentationRegistry.getInstrumentation().targetContext
  private val saveShots = InstrumentationRegistry.getArguments().getString("fitShots") == "true"

  /** The real clock: the update time shows only the time when it is today. */
  private val now = System.currentTimeMillis()

  /** The owner's real sizes (dp) per layout; 5×4 shows the stretched 5×3 layout. */
  private val ownerSizes =
    mapOf(
      LayoutSpec.L4x2 to listOf(267f to 132f),
      LayoutSpec.L5x2 to listOf(340f to 132f),
      LayoutSpec.L4x3 to listOf(267f to 205f),
      LayoutSpec.L5x3 to listOf(340f to 205f, 340f to 278f),
    )

  /**
   * The sizes each layout is drawn at here: just under its bucket (Android still shows the bucket
   * 1 dp below it), the bucket, and the owner's sizes.
   */
  private fun sizes(spec: LayoutSpec): List<Pair<Float, Float>> =
    listOf(spec.bucketWidth - 0.9f to spec.bucketHeight - 0.9f, spec.bucketWidth to spec.bucketHeight) +
      ownerSizes.getValue(spec)

  @Test
  fun everyText_fits_atEverySizeSettingAndLanguage() {
    val problems = mutableListOf<String>()
    for (lang in listOf("en", "uk")) {
      for (percent in listOf(10, 50, 100)) {
        val percents = FontPercents(percent, percent, percent, percent, percent)
        for ((name, model) in models(context(lang, 1f))) {
          for (spec in LayoutSpec.all) {
            for ((w, h) in sizes(spec)) {
              val view = render(context(lang, 1f), spec, w, h, model, percents, ThemeChoice.LIGHT)
              val at = "$lang $percent% $name ${spec.name} at ${w}x$h"
              clipped(view).forEach { problems += "$at: $it" }
              val data = model as? WidgetModel.Data
              if (data != null && (w to h) in ownerSizes.getValue(spec)) {
                val description = context(lang, 1f).resources.getQuantityString(io.lumensync.habattery.R.plurals.widget_soc_description, data.socPercent, data.socPercent)
                ringInkOutside(view, spec, description).forEach { problems += "$at: $it" }
              }
            }
          }
        }
      }
    }
    assertTrue(problems.joinToString("\n"), problems.isEmpty())
  }

  @Test
  fun largeSystemFont_everyTextFits() {
    val problems = mutableListOf<String>()
    for (lang in listOf("en", "uk")) {
      for (percent in listOf(50, 100)) {
        val percents = FontPercents(percent, percent, percent, percent, percent)
        for ((name, model) in models(context(lang, 1.3f))) {
          for (spec in LayoutSpec.all) {
            val view = render(context(lang, 1.3f), spec, spec.bucketWidth, spec.bucketHeight, model, percents, ThemeChoice.LIGHT)
            clipped(view).forEach { problems += "1.3 $lang $percent% $name ${spec.name}: $it" }
          }
        }
      }
    }
    assertTrue(problems.joinToString("\n"), problems.isEmpty())
  }

  /**
   * Re-review finding 1, with the real fonts: in every layout, English and Ukrainian, a raised
   * setting never shows a text smaller than at 50 %.
   */
  @Test
  fun raisedSettings_noTextSmallerThanAtFifty() {
    val raised =
      listOf(
        FontPercents(soc = 100),
        FontPercents(underSoc = 100),
        FontPercents(small = 100),
        FontPercents(100, 100, 100, 100, 100),
      )
    val problems = mutableListOf<String>()
    for (lang in listOf("en", "uk")) {
      val context = context(lang, 1f)
      val measurer = PaintTextMeasurer(context)
      val texts = WidgetTexts(context, io.lumensync.habattery.format.Formatters(context))
      for ((name, model) in models(context).filterValues { it is WidgetModel.Data }) {
        val m = model as WidgetModel.Data
        val t = DataTexts.of(m, texts)
        for (spec in LayoutSpec.all) {
          val at50 = sizesOf(WidgetFonts.data(m, t, spec, FontPercents(), 1f, measurer))
          raised.forEach { p ->
            val at = sizesOf(WidgetFonts.data(m, t, spec, p, 1f, measurer))
            at50.indices.forEach { i ->
              if (at[i] < at50[i] - 1e-3f) problems += "$lang $name ${spec.name} $p: text $i ${at[i]} < ${at50[i]}"
            }
          }
        }
      }
    }
    assertTrue(problems.joinToString("\n"), problems.isEmpty())
  }

  private fun sizesOf(f: DataFonts) = listOf(f.small, f.ringLabel, f.soc, f.underSoc, f.voltage, f.main) + f.cells

  /**
   * The picker preview (a static layout at the 4×2 bucket size): its ring text stays inside the
   * ring's inner circle, in English and Ukrainian (1 dp tolerance, as the widget).
   */
  @Test
  fun pickerPreview_ringTextStaysInside() {
    val problems = mutableListOf<String>()
    for (lang in listOf("en", "uk")) {
      val context = context(lang, 1f)
      val root = android.view.LayoutInflater.from(context).inflate(io.lumensync.habattery.R.layout.battery_widget_preview, FrameLayout(context), false)
      val density = context.resources.displayMetrics.density
      val w = (LayoutSpec.L4x2.bucketWidth * density).roundToInt()
      val h = (LayoutSpec.L4x2.bucketHeight * density).roundToInt()
      root.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
      root.layout(0, 0, w, h)
      val image = find(root) { it is android.widget.ImageView && it.parent is FrameLayout && (it.parent as ViewGroup).childCount == 4 }
      if (image == null) {
        problems += "$lang: preview ring missing"
        continue
      }
      inkOutsideCircle(root, image.parent as View, image, LayoutSpec.L4x2).forEach { problems += "$lang: $it" }
    }
    assertTrue(problems.joinToString("\n"), problems.isEmpty())
  }

  /** Review finding 7: a glyph from a fallback font (Myanmar, a tall font) counts in the line. */
  @Test
  fun fallbackFonts_countInTheLineMetrics() {
    // Below Android 13 the main font's metrics are intended (the per-text call is new in 13).
    Assume.assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
    val m = PaintTextMeasurer(context("en", 1f))
    val latin = m.metrics("12", 20f, TextWeight.MEDIUM)
    val mixed = m.metrics("12 \u1000", 20f, TextWeight.MEDIUM)
    assertTrue("$latin vs $mixed", mixed.bottom - mixed.top > latin.bottom - latin.top)
  }

  /** Glance drops the 11th child of a Row; both buttons and every pill must reach the RemoteViews. */
  @Test
  fun everyPart_reachesTheHomeScreen() {
    val context = context("en", 1f)
    val wanted =
      listOf(
        context.getString(io.lumensync.habattery.R.string.widget_auto_on),
        context.getString(io.lumensync.habattery.R.string.widget_refresh_action),
        context.getString(io.lumensync.habattery.R.string.widget_balancing_off),
        context.getString(io.lumensync.habattery.R.string.widget_battery_temperature),
        context.getString(io.lumensync.habattery.R.string.widget_mosfet_temperature),
        context.getString(io.lumensync.habattery.R.string.widget_discharging),
      )
    for (spec in LayoutSpec.all) {
      val view = render(context, spec, spec.bucketWidth, spec.bucketHeight, models(context).getValue("typical"), FontPercents(), ThemeChoice.LIGHT)
      val found = descriptions(view)
      wanted.forEach { assertTrue("${spec.name}: '$it' missing in $found", it in found) }
    }
  }

  /**
   * At 50 % the design PNGs' values show at their 50 % size: the design size × the group's base.
   * The pills match the PNGs; the small texts are 1.5 × theirs. The ring lines may give up to
   * [RING_SHRINK] of it: with the larger "SOC" label and the 6 dp gap, the owner accepts a kWh line
   * a little under its size in the small 4×2 ring. The top row's two time texts are left out: their
   * length depends on the clock format and on the time-left format, and they shrink to fit.
   */
  @Test
  fun fiftyPercent_designValues_showTheirFiftyPercentSizes() {
    val context = context("en", 1f)
    val measurer = PaintTextMeasurer(context)
    val texts = WidgetTexts(context, io.lumensync.habattery.format.Formatters(context))
    val m = models(context).getValue("typical") as WidgetModel.Data
    val problems = mutableListOf<String>()
    for (spec in LayoutSpec.all) {
      val pills = DataTexts.of(m, texts).cells
      val f = WidgetFonts.data(m, DataTexts.of(m, texts), spec, FontPercents(), 1f, measurer)
      val d = spec.fonts
      fun fifty(group: FontGroup, design: Float) = FontPercents().requestedDp(group, design, 1f)
      val ring =
        mapOf(
          "ringLabel" to (f.ringLabel to fifty(FontGroup.SMALL, d.ringLabel)),
          "soc" to (f.soc to fifty(FontGroup.SOC, d.soc)),
          "underSoc" to (f.underSoc to fifty(FontGroup.UNDER_SOC, d.underSoc)),
        )
      // Spec 0010 §4 (owner accepted it on the phone, 2026-11-20): a level icon, or the voltage
      // pill's direction arrow, takes room from the text, so those pills may sit under their design
      // size. The everyText_fits checks still guard them against clipping.
      val voltage =
        if (m.flow == WidgetModel.Flow.NONE) mapOf("voltage" to (f.voltage to fifty(FontGroup.MAIN, d.main)))
        else emptyMap()
      val outside =
        voltage +
          mapOf("main" to (f.main to fifty(FontGroup.MAIN, d.main))) +
          f.cells
            .mapIndexedNotNull { i, s ->
              if (pills[i].levelIcon == null) "cell$i" to (s to fifty(FontGroup.CELL, d.cell)) else null
            }
            .toMap()
      fun check(shown: Map<String, Pair<Float, Float>>, share: Float) =
        shown.forEach { (role, pair) ->
          val least = pair.second * share
          if (pair.first < least - 0.01f) problems += "${spec.name} $role ${pair.first} < $least"
        }
      check(ring, 1f - RING_SHRINK)
      check(outside, 1f)
    }
    assertTrue(problems.joinToString("\n"), problems.isEmpty())
  }

  /** Saves the drawings next to the design PNGs: light and dark, normal and sync error, 50 %. */
  @Test
  fun shots() {
    Assume.assumeTrue("run with -e fitShots true", saveShots)
    val dir = File(target.getExternalFilesDir(null), "fit").apply { mkdirs() }
    for (lang in listOf("en", "uk")) {
      val context = context(lang, 1f)
      val models = models(context)
      for (spec in LayoutSpec.all) {
        for ((w, h) in sizes(spec)) {
          for (theme in listOf(ThemeChoice.LIGHT, ThemeChoice.DARK)) {
            for (name in listOf("typical", "error", "worst-current", "worst-power", "nodata", "setup")) {
              for (percent in listOf(50, 100)) {
                if (percent == 100 && (theme == ThemeChoice.DARK || name in listOf("error", "nodata", "setup"))) continue
                val percents = FontPercents(percent, percent, percent, percent, percent)
                val view = render(context, spec, w, h, models.getValue(name), percents, theme)
                val file = "${spec.name}_${w.roundToInt()}x${h.roundToInt()}_${theme.name.lowercase()}_${name}_${percent}_$lang.png"
                save(view, File(dir, file))
              }
            }
          }
        }
      }
    }
  }

  private fun context(lang: String, fontScale: Float): Context = fitContext(target, lang, fontScale)

  private fun settings(sensors: Int, display: DisplaySettings = DisplaySettings()) =
    Settings(
      connection = ConnectionSettings(url = "http://ha.local:8123"),
      entities = EntitySettings(charge = "sensor.soc", temperatures = List(sensors) { "sensor.t$it" }),
      refresh = RefreshSettings(auto = true, intervalMinutes = 15),
      capacityAh = 314.0,
      display = display,
    )

  private fun model(reading: BatteryReading?, settings: Settings, error: SyncError? = null, hasToken: Boolean = true) =
    WidgetModel.build(BatteryOverview.build(CacheState(reading, error, null), settings, hasToken, now), settings)

  private val typical =
    BatteryReading(
      chargePercent = 55.0,
      currentA = -10.0,
      powerW = -265.0,
      totalVoltageV = 26.5,
      capacityRemainingAh = 172.7,
      averageCellVoltageV = 3.312,
      deltaCellVoltageV = 0.012,
      mosfetTemperatureC = 27.0,
      temperaturesC = listOf(25.0),
      balancing = false,
      updatedAtMillis = now - 60_000L,
    )

  private fun worst(charging: Boolean) =
    BatteryReading(
      chargePercent = 100.0,
      currentA = if (charging) 125.5 else -125.5,
      powerW = if (charging) 3980.0 else -3980.0,
      totalVoltageV = 26.5,
      capacityRemainingAh = 314.0,
      averageCellVoltageV = 3.651,
      deltaCellVoltageV = 0.1,
      mosfetTemperatureC = -20.0,
      temperaturesC = listOf(-20.0),
      balancing = true,
      // Yesterday: the update time shows the date too.
      updatedAtMillis = now - 30 * 3_600_000L,
    )

  /** Typical and worst-case models; also 3 cell pills, a sync error and the empty states. */
  private fun models(context: Context): Map<String, WidgetModel> =
    mapOf(
      "typical" to model(typical, settings(1)),
      "error" to model(typical, settings(1), SyncError.Unreachable),
      "worst-current" to model(worst(false), settings(1, DisplaySettings(UnderSoc.AH, MainValue.CURRENT))),
      "worst-power" to model(worst(true), settings(1, DisplaySettings(UnderSoc.KWH, MainValue.POWER))),
      "worst-3pills" to model(worst(false), settings(0, DisplaySettings(UnderSoc.AH, MainValue.CURRENT))),
      "worst-error" to model(worst(false), settings(1, DisplaySettings(UnderSoc.AH, MainValue.CURRENT)), SyncError.HttpError(503)),
      "nodata" to model(null, settings(1), SyncError.Unreachable),
      "setup" to model(null, settings(1), hasToken = false),
    )

  private fun render(
    context: Context,
    spec: LayoutSpec,
    widthDp: Float,
    heightDp: Float,
    model: WidgetModel,
    percents: FontPercents,
    theme: ThemeChoice,
  ): View {
    val actions =
      WidgetActions(
        actionStartActivity<MainActivity>(),
        actionStartActivity<MainActivity>(),
        actionStartActivity<MainActivity>(),
      )
    val widget = FitWidget { WidgetContent(model, percents, WidgetColors(theme), actions) }
    val views = runBlocking { widget.compose(context, size = DpSize(spec.bucketWidth.dp, spec.bucketHeight.dp)) }
    val host = FrameLayout(context)
    val view = views.apply(context, host)
    val density = context.resources.displayMetrics.density
    val w = (widthDp * density).roundToInt()
    val h = (heightDp * density).roundToInt()
    view.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
    view.layout(0, 0, w, h)
    return view
  }

  /**
   * At the design size the ring's text ink stays inside the ring's inner circle (1 dp tolerance,
   * spec 0005, 5.5). The ring is the view with the ring's description; its track image is drawn
   * fit-centre, so the circle is the image's largest centred square.
   */
  private fun ringInkOutside(root: View, spec: LayoutSpec, description: String): List<String> {
    val ring = find(root) { it.contentDescription?.toString() == description } ?: return listOf("ring missing")
    val image = find(ring) { it is android.widget.ImageView } ?: return listOf("ring image missing")
    return inkOutsideCircle(root, ring, image, spec)
  }

  /** Text in [group] whose ink leaves the circle drawn fit-centre by [image] ([spec]'s stroke). */
  private fun inkOutsideCircle(root: View, group: View, image: View, spec: LayoutSpec): List<String> {
    val box = rectIn(image, root)
    val d = minOf(box.width(), box.height())
    val inner = d / 2 - spec.ringStroke / spec.ring * d
    val density = root.resources.displayMetrics.density
    val found = mutableListOf<String>()
    fun visit(v: View) {
      if (v is TextView && v.text.isNotEmpty()) {
        val ink = contentBox(v, root)
        val corners = listOf(ink.left to ink.top, ink.right to ink.top, ink.left to ink.bottom, ink.right to ink.bottom)
        val far = corners.maxOf { (x, y) -> kotlin.math.hypot(x - box.centerX(), y - box.centerY()) }
        // Views are laid out in whole pixels: 1 px of rounding on top of the tolerance.
        if (far > inner + TextFit.RING_TOLERANCE_DP * density + 1f) found += "'${v.text}' leaves the ring by ${(far - inner) / density} dp"
      }
      if (v is ViewGroup) for (i in 0 until v.childCount) visit(v.getChildAt(i))
    }
    visit(group)
    return found
  }

  private fun find(view: View, match: (View) -> Boolean): View? {
    if (match(view)) return view
    if (view is ViewGroup) for (i in 0 until view.childCount) find(view.getChildAt(i), match)?.let { return it }
    return null
  }

  private fun save(view: View, file: File) {
    val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
    Canvas(bitmap).apply {
      drawColor(Color.TRANSPARENT)
      view.draw(this)
    }
    file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    assertEquals(true, file.exists())
  }

  private companion object {
    /** The share of its 50 % size a ring line may give up to fit the ring (owner decision). */
    const val RING_SHRINK = 0.05f
  }
}
