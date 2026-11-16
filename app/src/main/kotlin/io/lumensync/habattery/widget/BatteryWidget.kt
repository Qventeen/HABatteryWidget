package io.lumensync.habattery.widget

import android.content.Context
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import io.lumensync.habattery.MainActivity
import io.lumensync.habattery.R
import io.lumensync.habattery.container
import io.lumensync.habattery.domain.BatteryOverview
import io.lumensync.habattery.domain.WidgetModel
import io.lumensync.habattery.domain.model.HomeScale
import kotlinx.coroutines.flow.first

/** Renders from local data only (cache, settings, token presence). No network here. */
class BatteryWidget : GlanceAppWidget(errorUiLayout = R.layout.widget_error) {
  /**
   * One drawing per v5 layout; the home screen shows the one made for the widget's real size
   * (spec 0005, A1; checked on the owner's phone, 5.0).
   */
  override val sizeMode: SizeMode = SizeMode.Responsive(LayoutSpec.all.map { it.bucket }.toSet())

  override suspend fun provideGlance(context: Context, id: GlanceId) {
    val container = context.container
    // First values are read up front, so the first frame shows real data, not empty defaults.
    val initialCache = container.readingCache.state.first()
    val initialSettings = container.settingsRepository.settings.first()
    val initialHasToken = container.tokenStore.hasToken.first()
    val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)

    provideContent {
      val cache by container.readingCache.state.collectAsState(initialCache)
      val settings by container.settingsRepository.settings.collectAsState(initialSettings)
      val hasToken by container.tokenStore.hasToken.collectAsState(initialHasToken)
      val config by container.configWatcher.config.collectAsState()
      val overview = BatteryOverview.build(cache, settings, hasToken, System.currentTimeMillis())
      // A new key makes a new composition, also in a live session: the texts come from resources,
      // and their fitted sizes and the sp handed to Glance from the font scale.
      key(config) {
        WidgetContent(
          model = WidgetModel.build(overview, settings),
          fontPercents = settings.fontPercents,
          colors = remember(settings.appearance.theme) { WidgetColors(settings.appearance.theme) },
          actions =
            WidgetActions(
              // Glance puts each parameter into the activity intent as an extra named by the key.
              openApp =
                actionStartActivity<MainActivity>(actionParametersOf(TappedWidgetKey to appWidgetId)),
              refresh = actionRunCallback<RefreshAction>(),
              toggleAuto = actionRunCallback<ToggleAutoRefreshAction>(),
            ),
          hostScale = HomeScale.factor(settings.homeScalePercent),
        )
      }
    }
  }
}

/**
 * Extra on the intent that opens the app from a tap: the id of the tapped widget, so the app opens
 * on that widget's size. Not `EXTRA_APPWIDGET_ID`: that one means "configure this widget".
 */
const val EXTRA_TAPPED_WIDGET_ID = "io.lumensync.habattery.extra.TAPPED_WIDGET_ID"

private val TappedWidgetKey = ActionParameters.Key<Int>(EXTRA_TAPPED_WIDGET_ID)
