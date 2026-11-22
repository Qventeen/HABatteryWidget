package io.lumensync.habattery.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.glance.GlanceId
import androidx.glance.LocalSize
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import io.lumensync.habattery.MainActivity
import io.lumensync.habattery.R
import io.lumensync.habattery.domain.InverterWidgetModel
import io.lumensync.habattery.domain.model.CacheState
import io.lumensync.habattery.domain.model.HomeScale
import io.lumensync.habattery.domain.model.Settings

/** The inverter strip. Renders from local data only (cache, settings, token presence). */
class InverterWidget : GlanceAppWidget(errorUiLayout = R.layout.widget_error) {
  override val sizeMode: SizeMode =
    SizeMode.Responsive(InverterLayoutSpec.entries.map { it.bucket }.toSet())

  override suspend fun provideGlance(context: Context, id: GlanceId) =
    provideStoredContent(context, id) { cache, settings, hasToken, nowMillis, appWidgetId ->
      InverterStrip(cache, settings, hasToken, nowMillis, appWidgetId)
    }
}

/**
 * Widget [appWidgetId] drawn from local data, at the strip made for its size: the model, and the
 * strip's font percents, the theme and the home-screen scale of [settings]. Tapping ↻ refreshes;
 * tapping elsewhere opens the app on this widget.
 */
@Composable
internal fun InverterStrip(
  cache: CacheState,
  settings: Settings,
  hasToken: Boolean,
  now: Long,
  appWidgetId: Int,
  measurer: TextMeasurer? = null,
) {
  val size = LocalSize.current
  InverterWidgetContent(
    model = InverterWidgetModel.from(cache, settings, hasToken, now),
    spec = InverterLayoutSpec.forSize(size.width.value, size.height.value),
    colors = remember(settings.appearance.theme) { WidgetColors(settings.appearance.theme) },
    fonts = settings.inverter.fontPercents,
    hostScale = HomeScale.factor(settings.homeScalePercent),
    // Glance puts each parameter into the activity intent as an extra named by the key.
    openApp = actionStartActivity<MainActivity>(actionParametersOf(TappedWidgetKey to appWidgetId)),
    refresh = actionRunCallback<RefreshAction>(),
    measurer = measurer,
  )
}
