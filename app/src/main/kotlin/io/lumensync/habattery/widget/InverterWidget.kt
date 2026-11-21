package io.lumensync.habattery.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.glance.GlanceId
import androidx.glance.LocalSize
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import io.lumensync.habattery.MainActivity
import io.lumensync.habattery.R
import io.lumensync.habattery.container
import io.lumensync.habattery.domain.InverterWidgetModel
import io.lumensync.habattery.domain.model.CacheState
import io.lumensync.habattery.domain.model.HomeScale
import io.lumensync.habattery.domain.model.Settings
import kotlinx.coroutines.flow.first

/** The inverter strip. Renders from local data only (cache, settings, token presence). */
class InverterWidget : GlanceAppWidget(errorUiLayout = R.layout.widget_error) {
  override val sizeMode: SizeMode =
    SizeMode.Responsive(InverterLayoutSpec.entries.map { it.bucket }.toSet())

  override suspend fun provideGlance(context: Context, id: GlanceId) {
    val container = context.container
    // First values are read up front, so the first frame shows real data, not empty defaults.
    val initialCache = container.readingCache.state.first()
    val initialSettings = container.settingsRepository.settings.first()
    val initialSavedTypes = container.tokenStore.savedTypes.first()
    val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)

    provideContent {
      val cache by container.readingCache.state.collectAsState(initialCache)
      val settings by container.settingsRepository.settings.collectAsState(initialSettings)
      val savedTypes by container.tokenStore.savedTypes.collectAsState(initialSavedTypes)
      val config by container.configWatcher.config.collectAsState()
      // A new key makes a new composition: the texts and their fitted sizes follow the language
      // and the font scale, as in the battery widget.
      key(config) {
        val hasToken = settings.connection.tokenType in savedTypes
        InverterStrip(cache, settings, hasToken, appWidgetId)
      }
    }
  }
}

/**
 * Widget [appWidgetId] drawn from local data, at the strip made for its size: the model, and the
 * strip's font percents, the theme and the home-screen scale of [settings]. Tapping it opens the
 * app on this widget.
 */
@Composable
internal fun InverterStrip(
  cache: CacheState,
  settings: Settings,
  hasToken: Boolean,
  appWidgetId: Int,
  measurer: TextMeasurer? = null,
) {
  val size = LocalSize.current
  InverterWidgetContent(
    model = InverterWidgetModel.from(cache, settings, hasToken),
    spec = InverterLayoutSpec.forSize(size.width.value, size.height.value),
    colors = remember(settings.appearance.theme) { WidgetColors(settings.appearance.theme) },
    fonts = settings.inverter.fontPercents,
    hostScale = HomeScale.factor(settings.homeScalePercent),
    // Glance puts each parameter into the activity intent as an extra named by the key.
    openApp = actionStartActivity<MainActivity>(actionParametersOf(TappedWidgetKey to appWidgetId)),
    measurer = measurer,
  )
}
