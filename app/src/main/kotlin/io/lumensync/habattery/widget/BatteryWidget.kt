package io.lumensync.habattery.widget

import android.content.Context
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.glance.GlanceId
import androidx.glance.GlanceTheme
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import io.lumensync.habattery.MainActivity
import io.lumensync.habattery.R
import io.lumensync.habattery.container
import io.lumensync.habattery.domain.BatteryOverview
import kotlinx.coroutines.flow.first

/** Renders from local data only (cache, settings, token presence). No network here. */
class BatteryWidget : GlanceAppWidget(errorUiLayout = R.layout.widget_error) {
  override val sizeMode: SizeMode = SizeMode.Exact

  override suspend fun provideGlance(context: Context, id: GlanceId) {
    val container = context.container
    // First values are read up front, so the first frame shows real data, not empty defaults.
    val initialCache = container.readingCache.state.first()
    val initialSettings = container.settingsRepository.settings.first()
    val initialHasToken = container.tokenStore.hasToken.first()

    provideContent {
      val cache by container.readingCache.state.collectAsState(initialCache)
      val settings by container.settingsRepository.settings.collectAsState(initialSettings)
      val hasToken by container.tokenStore.hasToken.collectAsState(initialHasToken)
      GlanceTheme {
        WidgetContent(
          overview = BatteryOverview.build(cache, settings, hasToken, System.currentTimeMillis()),
          refreshAction = actionRunCallback<RefreshAction>(),
          openAppAction = actionStartActivity<MainActivity>(),
        )
      }
    }
  }
}
