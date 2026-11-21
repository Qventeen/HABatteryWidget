package io.lumensync.habattery.widget

import android.content.Context
import android.view.View
import android.widget.FrameLayout
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.compose
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.lumensync.habattery.MainActivity
import io.lumensync.habattery.R
import io.lumensync.habattery.domain.InverterWidgetModel
import io.lumensync.habattery.domain.model.CacheState
import io.lumensync.habattery.domain.model.ConnectionSettings
import io.lumensync.habattery.domain.model.InverterEntitySettings
import io.lumensync.habattery.domain.model.InverterFontPercents
import io.lumensync.habattery.domain.DefaultTextLevels
import io.lumensync.habattery.domain.model.TextValue
import io.lumensync.habattery.domain.model.InverterReading
import io.lumensync.habattery.domain.model.InverterSettings
import io.lumensync.habattery.domain.model.ModeStyle
import io.lumensync.habattery.domain.model.Settings
import io.lumensync.habattery.domain.model.SyncError
import io.lumensync.habattery.domain.model.ThemeChoice
import io.lumensync.habattery.format.Formatters
import kotlin.math.roundToInt
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The strip's fit check, as [WidgetFitTest] for the battery widget: the real strip composed by
 * Glance at each bucket, laid out at the bucket and at the design card sizes, with typical and
 * worst-case values, both mode styles, both font settings at 10, 50 and 100 %, in English and
 * Ukrainian. Every text must show whole; only a mode text longer than its box may end with "…".
 *
 * The worst case is a mode text the table does not know and too long for its box ([LONG_MODE]):
 * it gives the widest mode pill, so with 288 V, 300 V and the sync error icon the voltage pills
 * have the least room. "Unknown" itself is no value (`ModeText`) and shows "—".
 */
@RunWith(AndroidJUnit4::class)
class InverterWidgetFitTest {
  private val target: Context = InstrumentationRegistry.getInstrumentation().targetContext

  /** Just under the bucket (Android still shows it 1 dp below), the bucket, and the design card. */
  private fun sizes(spec: InverterLayoutSpec): List<Pair<Float, Float>> =
    listOf(
      spec.bucketWidthDp - 0.9f to spec.bucketHeightDp - 0.9f,
      spec.bucketWidthDp to spec.bucketHeightDp,
      spec.bucketWidthDp + LayoutSpec.BUCKET_MARGIN_DP to spec.bucketHeightDp + LayoutSpec.BUCKET_MARGIN_DP,
    )

  @Test
  fun everyText_fits_atEverySizeSettingAndLanguage() {
    val problems = mutableListOf<String>()
    for (lang in listOf("en", "uk")) {
      for (percent in listOf(10, 50, 100)) {
        for ((name, model) in models()) {
          for (spec in InverterLayoutSpec.entries) {
            for ((w, h) in sizes(spec)) {
              val view = render(context(lang, 1f), spec, w, h, model, InverterFontPercents(percent, percent))
              clipped(view, mayEllipsize = { it == LONG_MODE }).forEach { problems += "$lang $percent% $name ${spec.name} at ${w}x$h: $it" }
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
        for ((name, model) in models()) {
          for (spec in InverterLayoutSpec.entries) {
            val view =
              render(context(lang, 1.3f), spec, spec.bucketWidthDp, spec.bucketHeightDp, model, InverterFontPercents(percent, percent))
            clipped(view, mayEllipsize = { it == LONG_MODE }).forEach { problems += "1.3 $lang $percent% $name ${spec.name}: $it" }
          }
        }
      }
    }
    assertTrue(problems.joinToString("\n"), problems.isEmpty())
  }

  /**
   * At 50 % every word of the mode table shows its design size from 4×1 on, in English and
   * Ukrainian (spec 0012: the words are short so they fit there). At 3×1 a word as wide as its
   * box may shrink a little.
   */
  @Test
  fun fiftyPercent_modeWords_showTheirDesignSize() {
    val problems = mutableListOf<String>()
    for (lang in listOf("en", "uk")) {
      val context = context(lang, 1f)
      val measurer = PaintTextMeasurer(context)
      for (mode in MODES) {
        val m = model(reading(mode.texts.first(), 228.0, 228.0), ModeStyle.ICON_WORD) as InverterWidgetModel.Data
        val texts = StripTexts.of(m, context, Formatters(context))
        for (spec in listOf(InverterLayoutSpec.S4X1, InverterLayoutSpec.S5X1)) {
          val fonts = InverterFonts.data(m.style, texts, spec, 1f, InverterFontPercents(), 1f, measurer)
          if (fonts.word < spec.design.wordFont - 0.01f) problems += "$lang ${texts.word} ${spec.name}: ${fonts.word}"
        }
      }
    }
    assertTrue(problems.joinToString("\n"), problems.isEmpty())
  }

  /** Glance drops the 11th child of a Row; every described part must reach the RemoteViews. */
  @Test
  fun everyPart_reachesTheHomeScreen() {
    val context = context("en", 1f)
    val wanted =
      listOf(
        context.getString(R.string.mode_word_mains),
        context.getString(R.string.inverter_grid_voltage),
        context.getString(R.string.inverter_output_voltage),
        context.getString(R.string.widget_sync_error, Formatters(context).error(SyncError.Unreachable)),
      )
    val model = model(reading("Mains", 228.0, 228.0), ModeStyle.BIG_ICON, SyncError.Unreachable)
    for (spec in InverterLayoutSpec.entries) {
      val found = descriptions(render(context, spec, spec.bucketWidthDp, spec.bucketHeightDp, model, InverterFontPercents()))
      wanted.forEach { assertTrue("${spec.name}: '$it' missing in $found", it in found) }
    }
  }

  private fun context(lang: String, fontScale: Float): Context = fitContext(target, lang, fontScale)

  private fun settings(style: ModeStyle) =
    Settings(
      connection = ConnectionSettings(url = "http://ha.local:8123"),
      inverter =
        InverterSettings(
          entities = InverterEntitySettings(mode = "sensor.m", gridVoltage = "sensor.g", outputVoltage = "sensor.o"),
          modeStyle = style,
        ),
    )

  private fun reading(mode: String?, grid: Double?, output: Double?) =
    InverterReading(mode, grid, output, updatedAtMillis = System.currentTimeMillis())

  private fun model(reading: InverterReading?, style: ModeStyle, error: SyncError? = null, hasToken: Boolean = true) =
    InverterWidgetModel.from(CacheState(inverterReading = reading, inverterError = error), settings(style), hasToken)

  /** Typical and worst-case strips in both mode styles, and the empty states. */
  private fun models(): Map<String, InverterWidgetModel> =
    ModeStyle.entries.flatMap { style ->
      val s = style.name.lowercase()
      listOf(
        "typical-$s" to model(reading("Mains", 228.0, 228.0), style),
        "offgrid-$s" to model(reading("Off-Grid", 0.0, 230.0), style),
        "worst-$s" to model(reading(LONG_MODE, 288.0, 300.0), style, SyncError.HttpError(503)),
        "missing-$s" to model(reading(null, null, null), style, SyncError.Unreachable),
      ) + MODES.map { mode -> "${mode.defaultName!!.name.lowercase()}-$s" to model(reading(mode.texts.first(), 228.0, 0.0), style) }
    }.toMap() +
      mapOf(
        "nodata" to model(null, ModeStyle.BIG_ICON, SyncError.Unreachable),
        "setup" to model(null, ModeStyle.BIG_ICON, hasToken = false),
      )

  private fun render(
    context: Context,
    spec: InverterLayoutSpec,
    widthDp: Float,
    heightDp: Float,
    model: InverterWidgetModel,
    percents: InverterFontPercents,
  ): View {
    val widget =
      FitWidget {
        InverterWidgetContent(
          model,
          spec,
          WidgetColors(ThemeChoice.LIGHT),
          percents,
          hostScale = 1f,
          openApp = actionStartActivity<MainActivity>(),
        )
      }
    val views = runBlocking { widget.compose(context, size = spec.bucket) }
    val view = views.apply(context, FrameLayout(context))
    val density = context.resources.displayMetrics.density
    val w = (widthDp * density).roundToInt()
    val h = (heightDp * density).roundToInt()
    view.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
    view.layout(0, 0, w, h)
    return view
  }

  private companion object {
    const val LONG_MODE = "Battery discharge"

    /** The built-in modes with their words; "other" shows the raw text and has its own cases. */
    val MODES = DefaultTextLevels.of(TextValue.MODE).filterNot { it.other }
  }
}
