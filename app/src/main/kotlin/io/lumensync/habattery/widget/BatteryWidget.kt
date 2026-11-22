package io.lumensync.habattery.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import io.lumensync.habattery.MainActivity
import io.lumensync.habattery.R
import io.lumensync.habattery.domain.BatteryOverview
import io.lumensync.habattery.domain.WidgetModel
import io.lumensync.habattery.domain.model.CacheState
import io.lumensync.habattery.domain.model.HomeScale
import io.lumensync.habattery.domain.model.Settings

/** Renders from local data only (cache, settings, token presence). No network here. */
class BatteryWidget : GlanceAppWidget(errorUiLayout = R.layout.widget_error) {
  /**
   * One drawing per v5 layout; the home screen shows the one made for the widget's real size
   * (spec 0005, A1; checked on the owner's phone, 5.0).
   */
  override val sizeMode: SizeMode = SizeMode.Responsive(LayoutSpec.all.map { it.bucket }.toSet())

  override suspend fun provideGlance(context: Context, id: GlanceId) =
    provideStoredContent(context, id) { cache, settings, hasToken, appWidgetId ->
      BatteryWidgetContent(cache, settings, hasToken, System.currentTimeMillis(), appWidgetId)
    }
}

/**
 * Widget [appWidgetId] drawn from local data: the model, and the font percents, the theme and the
 * home-screen scale of [settings]. Tapping the card opens the app on this widget's size.
 */
@Composable
internal fun BatteryWidgetContent(
  cache: CacheState,
  settings: Settings,
  hasToken: Boolean,
  now: Long,
  appWidgetId: Int,
  measurer: TextMeasurer? = null,
) {
  val overview = BatteryOverview.build(cache, settings, hasToken, now)
  WidgetContent(
    model = WidgetModel.build(overview, settings),
    fontPercents = settings.fontPercents,
    colors = remember(settings.appearance.theme) { WidgetColors(settings.appearance.theme) },
    actions =
      WidgetActions(
        // Glance puts each parameter into the activity intent as an extra named by the key.
        openApp = actionStartActivity<MainActivity>(actionParametersOf(TappedWidgetKey to appWidgetId)),
        refresh = actionRunCallback<RefreshAction>(),
      ),
    hostScale = HomeScale.factor(settings.homeScalePercent),
    measurer = measurer,
  )
}

/**
 * Extra on the intent that opens the app from a tap: the id of the tapped widget, so the app opens
 * on that widget's size. Not `EXTRA_APPWIDGET_ID`: that one means "configure this widget".
 */
const val EXTRA_TAPPED_WIDGET_ID = "io.lumensync.habattery.extra.TAPPED_WIDGET_ID"

internal val TappedWidgetKey = ActionParameters.Key<Int>(EXTRA_TAPPED_WIDGET_ID)
