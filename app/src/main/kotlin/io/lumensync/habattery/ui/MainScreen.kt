package io.lumensync.habattery.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.lumensync.habattery.R
import io.lumensync.habattery.domain.BatteryOverview
import io.lumensync.habattery.domain.WidgetModel
import io.lumensync.habattery.domain.model.AppearanceSettings
import io.lumensync.habattery.domain.model.DisplaySettings
import io.lumensync.habattery.domain.model.EntitySettings
import io.lumensync.habattery.domain.model.FontPercents
import io.lumensync.habattery.domain.model.LanguageChoice
import io.lumensync.habattery.domain.LevelBasis
import io.lumensync.habattery.domain.model.LevelValue
import io.lumensync.habattery.domain.model.TokenType
import io.lumensync.habattery.domain.model.textSizeChanged
import io.lumensync.habattery.ui.sections.ActionsRow
import io.lumensync.habattery.ui.sections.AppearanceSection
import io.lumensync.habattery.ui.sections.CapacitySection
import io.lumensync.habattery.ui.sections.ColorsSection
import io.lumensync.habattery.ui.sections.ConnectionSection
import io.lumensync.habattery.ui.sections.EntitiesSection
import io.lumensync.habattery.ui.sections.NotificationSection
import io.lumensync.habattery.ui.sections.RefreshSection
import io.lumensync.habattery.ui.sections.TokenSection
import io.lumensync.habattery.ui.sections.WidgetPreviewCard
import io.lumensync.habattery.ui.sections.WidgetSection
import java.util.Locale

/** What the screen can ask for: the ViewModel's functions plus the system actions. */
data class MainActions(
  val refreshNow: () -> Unit,
  val saveConnection: (url: String) -> Unit,
  val saveToken: (token: String) -> Unit,
  val saveTokenType: (TokenType) -> Unit,
  val removeToken: () -> Unit,
  val saveEntities: (EntitySettings) -> Unit,
  val findTemperatureSensors: suspend (prefix: String, stillCurrent: () -> Boolean) -> SensorSearch,
  val saveRefresh: (auto: Boolean, intervalText: String) -> Unit,
  val setNotifyOnFull: (Boolean) -> Unit,
  val saveCapacity: (text: String, locale: Locale) -> Unit,
  val saveDisplay: (DisplaySettings) -> Unit,
  val saveFontPercents: (FontPercents) -> Unit,
  val saveHomeScale: (Int) -> Unit,
  val resetWidgetSettings: () -> Unit,
  val saveAppearance: (AppearanceSettings) -> Unit,
  val setLanguage: (LanguageChoice) -> Unit,
  val saveLevels: (LevelValue, List<LevelDraft>, Locale) -> Boolean,
  val useDefaults: (LevelValue) -> Unit,
  val requestPinWidget: () -> Unit,
  val openNotificationSettings: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
  state: MainUiState,
  actions: MainActions,
  language: LanguageChoice?,
  previewRequest: PreviewRequest,
) {
  Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.app_name)) }) }) { padding ->
    val overview = state.overview
    val widget = state.widget
    if (overview == null || widget == null) {
      // The first state from storage has not arrived yet; drafts must start from real settings.
      Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
      }
    } else {
      MainContent(state, overview, widget, actions, language, previewRequest, padding)
    }
  }
}

@Composable
private fun MainContent(
  state: MainUiState,
  overview: BatteryOverview,
  widget: WidgetModel,
  actions: MainActions,
  language: LanguageChoice?,
  previewRequest: PreviewRequest,
  padding: PaddingValues,
) {
  val settings = state.settings
  Column(
    modifier =
      Modifier.fillMaxSize()
        .padding(padding)
        .consumeWindowInsets(padding)
        .imePadding()
        .verticalScroll(rememberScrollState())
        .padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    WidgetPreviewCard(widget, settings.fontPercents, settings.appearance.theme, previewRequest)
    ActionsRow(state.syncing, actions.refreshNow, actions.requestPinWidget)
    WidgetSection(
      settings.display,
      settings.fontPercents,
      settings.homeScalePercent,
      settings.textSizeChanged,
      actions.saveDisplay,
      actions.saveFontPercents,
      actions.saveHomeScale,
      actions.resetWidgetSettings,
    )
    AppearanceSection(settings.appearance, language, actions.saveAppearance, actions.setLanguage)
    val reading = overview.reading
    ColorsSection(
      settings.colorLevels,
      LevelBasis.of(reading, settings),
      state.errors,
      actions.saveLevels,
      actions.useDefaults,
    )
    ConnectionSection(
      settings.connection,
      state.errors,
      actions.saveConnection,
      initiallyExpanded = overview.setupNeeded,
    )
    TokenSection(
      settings.connection.tokenType,
      state.hasToken,
      state.showTokenTypeHint,
      state.errors,
      actions.saveToken,
      actions.saveTokenType,
      actions.removeToken,
      initiallyExpanded = overview.setupNeeded,
    )
    EntitiesSection(
      settings.entities,
      state.errors,
      actions.saveEntities,
      actions.findTemperatureSensors,
      initiallyExpanded = overview.setupNeeded,
    )
    RefreshSection(settings.refresh, state.errors, actions.saveRefresh)
    NotificationSection(
      settings.notifyOnFull,
      actions.setNotifyOnFull,
      actions.openNotificationSettings,
    )
    CapacitySection(settings.capacityAh, state.errors, actions.saveCapacity)
  }
}
