package io.lumensync.habattery.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.provideContent
import io.lumensync.habattery.container
import io.lumensync.habattery.domain.model.CacheState
import io.lumensync.habattery.domain.model.Settings
import kotlinx.coroutines.flow.first

/**
 * Provides [content] from local data only (cache, settings, token presence), for every widget.
 * One place holds the token-presence rule, the recompose-on-config rule and the amber redraw, so
 * the widgets cannot drift apart.
 */
internal suspend fun GlanceAppWidget.provideStoredContent(
  context: Context,
  id: GlanceId,
  content: @Composable (cache: CacheState, settings: Settings, hasToken: Boolean, appWidgetId: Int) -> Unit,
) {
  val container = context.container
  // First values are read up front, so the first frame shows real data, not empty defaults.
  val initialCache = container.readingCache.state.first()
  val initialSettings = container.settingsRepository.settings.first()
  val initialSavedTypes = container.tokenStore.savedTypes.first()
  val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
  // Asked here, not in the composition: Responsive mode composes once per size, and each new ask
  // replaces the redraw work, even one that is running.
  container.lateRedraw()

  provideContent {
    val cache by container.readingCache.state.collectAsState(initialCache)
    val settings by container.settingsRepository.settings.collectAsState(initialSettings)
    val savedTypes by container.tokenStore.savedTypes.collectAsState(initialSavedTypes)
    val config by container.configWatcher.config.collectAsState()
    // A new key makes a new composition, also in a live session: the texts come from resources,
    // and their fitted sizes and the sp handed to Glance from the font scale.
    key(config) {
      val hasToken = settings.connection.tokenType in savedTypes
      content(cache, settings, hasToken, appWidgetId)
    }
  }
}
