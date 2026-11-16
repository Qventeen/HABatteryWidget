package io.lumensync.habattery.widget

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.RectF
import android.text.Layout
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.compose
import androidx.glance.appwidget.provideContent
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
import io.lumensync.habattery.domain.model.FontPercents
import io.lumensync.habattery.domain.model.MainValue
import io.lumensync.habattery.domain.model.RefreshSettings
import io.lumensync.habattery.domain.model.Settings
import io.lumensync.habattery.domain.model.SyncError
import io.lumensync.habattery.domain.model.ThemeChoice
import io.lumensync.habattery.domain.model.UnderSoc
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The fit check of spec 0005 (§6): the real widget composed by Glance at each bucket, laid out at
 * the bucket and at the owner's sizes, with worst-case and typical values, every font group at 25,
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

  private class FitWidget(private val content: @Composable () -> Unit) : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) = provideContent { content() }
  }

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
      for (percent in listOf(25, 50, 100)) {
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

  /** Review finding 7: a glyph from a fallback font (Myanmar, a tall font) counts in the line. */
  @Test
  fun fallbackFonts_countInTheLineMetrics() {
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

  private fun descriptions(view: View): List<String> =
    listOfNotNull(view.contentDescription?.toString()) +
      if (view is ViewGroup) (0 until view.childCount).flatMap { descriptions(view.getChildAt(it)) } else emptyList()

  /**
   * At 50 % the design PNGs' values show at the design sizes, so the drawing matches the PNGs. The
   * top row's two time texts are left out: their length depends on the clock format and on the
   * time-left format, and they shrink to fit (spec 0005 build notes).
   */
  @Test
  fun fiftyPercent_designValues_showTheDesignSizes() {
    val context = context("en", 1f)
    val measurer = PaintTextMeasurer(context)
    val texts = WidgetTexts(context, io.lumensync.habattery.format.Formatters(context))
    val m = models(context).getValue("typical") as WidgetModel.Data
    val problems = mutableListOf<String>()
    for (spec in LayoutSpec.all) {
      val f = WidgetFonts.data(m, DataTexts.of(m, texts), spec, FontPercents(), 1f, measurer)
      val d = spec.fonts
      val shown =
        mapOf(
          "ringLabel" to (f.ringLabel to d.ringLabel),
          "soc" to (f.soc to d.soc),
          "underSoc" to (f.underSoc to d.underSoc),
          "voltage" to (f.voltage to d.main),
          "main" to (f.main to d.main),
        ) + f.cells.mapIndexed { i, s -> "cell$i" to (s to d.cell) }
      shown.forEach { (role, pair) ->
        if (pair.first < pair.second - 0.01f) problems += "${spec.name} $role ${pair.first} < ${pair.second}"
      }
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

  private fun context(lang: String, fontScale: Float): Context {
    val config = Configuration(target.resources.configuration)
    config.setLocale(Locale.forLanguageTag(lang))
    config.fontScale = fontScale
    return target.createConfigurationContext(config)
  }

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

  /** Every text that does not show whole: wrapped, too narrow, or cut by a view around it. */
  private fun clipped(root: View): List<String> {
    val found = mutableListOf<String>()
    fun visit(view: View, ancestors: List<View>) {
      if (view.visibility != View.VISIBLE) return
      if (view is TextView && view.text.isNotEmpty()) {
        val text = view.text.toString()
        val layout = view.layout
        val needed = Layout.getDesiredWidth(view.text, view.paint)
        val room = view.width - view.totalPaddingLeft - view.totalPaddingRight
        if (layout == null || layout.getLineEnd(0) < text.length) found += "'$text' wraps"
        else if (needed > room + 0.5f) found += "'$text' needs $needed px, has $room"
        else {
          val box = contentBox(view, root)
          ancestors.forEach { a ->
            val clip = rectIn(a, root).apply {
              if (a is ViewGroup && a.clipToPadding) {
                left += a.paddingLeft; top += a.paddingTop; right -= a.paddingRight; bottom -= a.paddingBottom
              }
            }
            // Views are laid out in whole pixels: 1 px of rounding is not a cut.
            if (box.left < clip.left - 1f || box.right > clip.right + 1f || box.top < clip.top - 1f || box.bottom > clip.bottom + 1f) {
              found += "'$text' cut: $box outside $clip"
              return
            }
          }
        }
      }
      if (view is ViewGroup) for (i in 0 until view.childCount) visit(view.getChildAt(i), ancestors + view)
    }
    visit(root, emptyList())
    return found
  }

  /**
   * At the design size the ring's text ink stays inside the ring's inner circle (1 dp tolerance,
   * spec 0005, 5.5). The ring is the view with the ring's description; its track image is drawn
   * fit-centre, so the circle is the image's largest centred square.
   */
  private fun ringInkOutside(root: View, spec: LayoutSpec, description: String): List<String> {
    val ring = find(root) { it.contentDescription?.toString() == description } ?: return listOf("ring missing")
    val image = find(ring) { it is android.widget.ImageView } ?: return listOf("ring image missing")
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
    visit(ring)
    return found
  }

  private fun find(view: View, match: (View) -> Boolean): View? {
    if (match(view)) return view
    if (view is ViewGroup) for (i in 0 until view.childCount) find(view.getChildAt(i), match)?.let { return it }
    return null
  }

  /**
   * The ink of the line: from the line's left to its right, from the glyphs' top to their bottom
   * (the font's padding and an empty descent may be cut; nothing shows there).
   */
  private fun contentBox(view: TextView, root: View): RectF {
    val at = rectIn(view, root)
    val layout = view.layout
    val baseline = at.top + view.baseline
    val ink = android.graphics.Rect()
    view.paint.getTextBounds(view.text.toString(), 0, view.text.length, ink)
    return RectF(
      at.left + view.totalPaddingLeft + layout.getLineLeft(0),
      baseline + ink.top,
      at.left + view.totalPaddingLeft + layout.getLineRight(0),
      baseline + ink.bottom,
    )
  }

  private fun rectIn(view: View, root: View): RectF {
    var x = 0f
    var y = 0f
    var v: View = view
    while (v !== root) {
      x += v.left - v.scrollX
      y += v.top - v.scrollY
      v = v.parent as View
    }
    return RectF(x, y, x + view.width, y + view.height)
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

}
