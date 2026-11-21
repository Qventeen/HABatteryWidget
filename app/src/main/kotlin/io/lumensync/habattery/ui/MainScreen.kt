package io.lumensync.habattery.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import io.lumensync.habattery.R
import io.lumensync.habattery.domain.BatteryOverview
import io.lumensync.habattery.domain.InverterWidgetModel
import io.lumensync.habattery.domain.LevelBasis
import io.lumensync.habattery.domain.WidgetModel
import io.lumensync.habattery.domain.WidgetModel.EmptyKind
import io.lumensync.habattery.domain.model.AppearanceSettings
import io.lumensync.habattery.domain.model.BatteryType
import io.lumensync.habattery.domain.model.DisplaySettings
import io.lumensync.habattery.domain.model.EntitySettings
import io.lumensync.habattery.domain.model.FontPercents
import io.lumensync.habattery.domain.model.IconSlot
import io.lumensync.habattery.domain.model.InverterEntitySettings
import io.lumensync.habattery.domain.model.InverterFontPercents
import io.lumensync.habattery.domain.model.InverterIcon
import io.lumensync.habattery.domain.model.LanguageChoice
import io.lumensync.habattery.domain.model.LevelValue
import io.lumensync.habattery.domain.model.ModeStyle
import io.lumensync.habattery.domain.model.TokenType
import io.lumensync.habattery.domain.model.textSizeChanged
import io.lumensync.habattery.ui.sections.AddWidgetRow
import io.lumensync.habattery.ui.sections.AppearanceSection
import io.lumensync.habattery.ui.sections.PackSection
import io.lumensync.habattery.ui.sections.ColorsSection
import io.lumensync.habattery.ui.sections.ConnectionSection
import io.lumensync.habattery.ui.sections.EntitiesSection
import io.lumensync.habattery.ui.sections.HomeScaleSection
import io.lumensync.habattery.ui.sections.InverterDisplaySection
import io.lumensync.habattery.ui.sections.InverterEntitiesSection
import io.lumensync.habattery.ui.sections.InverterIconsSection
import io.lumensync.habattery.ui.sections.InverterPreviewCard
import io.lumensync.habattery.ui.sections.NotificationSection
import io.lumensync.habattery.ui.sections.RefreshSection
import io.lumensync.habattery.ui.sections.TokenSection
import io.lumensync.habattery.ui.sections.WidgetPreviewCard
import io.lumensync.habattery.ui.sections.WidgetSection
import io.lumensync.habattery.widget.BatteryWidgetReceiver
import io.lumensync.habattery.widget.InverterWidgetReceiver
import java.util.Locale

/** What the screen can ask for: the ViewModel's functions plus the system actions. */
data class MainActions(
  val refreshNow: () -> Unit,
  val saveConnection: (url: String) -> Unit,
  val saveToken: (token: String) -> Unit,
  val saveTokenType: (TokenType) -> Unit,
  val removeToken: () -> Unit,
  val saveEntities: (EntitySettings) -> Unit,
  val saveInverterEntities: (InverterEntitySettings) -> Unit,
  val findTemperatureSensors: suspend (prefix: String, stillCurrent: () -> Boolean) -> SensorSearch,
  val saveRefresh: (auto: Boolean, intervalText: String) -> Unit,
  val setNotificationsOn: (Boolean) -> Unit,
  val saveBatteryType: (BatteryType) -> Unit,
  val savePack: (cells: String, capacity: String, locale: Locale) -> Unit,
  val saveDisplay: (DisplaySettings) -> Unit,
  val saveFontPercents: (FontPercents) -> Unit,
  val saveHomeScale: (Int) -> Unit,
  val resetBatterySizes: () -> Unit,
  val resetHomeScale: () -> Unit,
  val saveModeStyle: (ModeStyle) -> Unit,
  val saveInverterFonts: (InverterFontPercents) -> Unit,
  val resetInverterSizes: () -> Unit,
  val saveInverterIcon: (IconSlot, InverterIcon) -> Unit,
  val saveAppearance: (AppearanceSettings) -> Unit,
  val setLanguage: (LanguageChoice) -> Unit,
  val saveLevels: (LevelValue, List<LevelDraft>, margin: String, Locale) -> Boolean,
  val useDefaults: (LevelValue) -> Unit,
  val clearErrors: () -> Unit,
  val requestPinWidget: (receiver: Class<out GlanceAppWidgetReceiver>) -> Unit,
  val openNotificationSettings: () -> Unit,
)

/** Test tag of [tab]'s item in the bottom bar. */
fun appTabTag(tab: AppTab): String = "app-tab-${tab.name}"

@Composable
fun MainScreen(
  state: MainUiState,
  actions: MainActions,
  language: LanguageChoice?,
  previewRequest: PreviewRequest,
  stripRequest: StripRequest,
  tabRequest: AppTabRequest,
) {
  val overview = state.overview
  val widget = state.widget
  val inverter = state.inverter
  if (overview == null || widget == null || inverter == null) {
    // The first state from storage has not arrived yet; drafts and the first tab need real settings.
    MainScaffold(state.syncing, actions.refreshNow) { padding ->
      Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
      }
    }
  } else {
    MainTabs(state, overview, widget, inverter, actions, language, previewRequest, stripRequest, tabRequest)
  }
}

@Composable
private fun MainTabs(
  state: MainUiState,
  overview: BatteryOverview,
  widget: WidgetModel,
  inverter: InverterWidgetModel,
  actions: MainActions,
  language: LanguageChoice?,
  previewRequest: PreviewRequest,
  stripRequest: StripRequest,
  tabRequest: AppTabRequest,
) {
  // Picked once from the first state: saving the URL and token on Common must not move the user.
  var tab by rememberSaveable {
    mutableStateOf(AppTabs.first(tabRequest.provider, state.connectionMissing, tabRequest.requested))
  }
  // The errors are one list for all tabs: a level editor's errors must not show in the other
  // widget tab's editor.
  fun show(next: AppTab) {
    if (next != tab) actions.clearErrors()
    tab = next
  }
  var seenCount by rememberSaveable { mutableIntStateOf(tabRequest.count) }
  LaunchedEffect(tabRequest.count) {
    show(tabRequest.tabFor(tab, seenCount, state.connectionMissing))
    seenCount = tabRequest.count
  }
  // Each tab keeps its scroll, folds and drafts while another tab is shown, and across rotation.
  val tabStates = rememberSaveableStateHolder()
  MainScaffold(
    state.syncing,
    actions.refreshNow,
    bottomBar = { AppTabBar(tab, ::show) },
  ) { padding ->
    val shown = tab
    tabStates.SaveableStateProvider(shown) {
      TabColumn(padding) {
        when (shown) {
          AppTab.BATTERY -> BatteryTab(state, overview, widget, actions, previewRequest)
          AppTab.INVERTER -> InverterTab(state, inverter, actions, stripRequest)
          AppTab.COMMON -> CommonTab(state, actions, language)
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScaffold(
  syncing: Boolean,
  onRefresh: () -> Unit,
  bottomBar: @Composable () -> Unit = {},
  content: @Composable (PaddingValues) -> Unit,
) {
  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text(stringResource(R.string.app_name)) },
        actions = { RefreshButton(syncing, onRefresh) },
      )
    },
    bottomBar = bottomBar,
    content = content,
  )
}

/** ↻ "Refresh now": one sync updates both widgets. Off, with a spinner, while a sync runs. */
@Composable
private fun RefreshButton(syncing: Boolean, onRefresh: () -> Unit) {
  val label = stringResource(R.string.action_refresh_now)
  IconButton(
    onClick = onRefresh,
    enabled = !syncing,
    modifier = Modifier.semantics { contentDescription = label },
  ) {
    if (syncing) {
      CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
    } else {
      Icon(painterResource(R.drawable.ic_refresh), contentDescription = null) // the button speaks
    }
  }
}

@Composable
private fun AppTabBar(selected: AppTab, onSelect: (AppTab) -> Unit) {
  NavigationBar {
    AppTab.entries.forEach { tab ->
      NavigationBarItem(
        selected = tab == selected,
        onClick = { onSelect(tab) },
        icon = { Icon(painterResource(tabIcon(tab)), contentDescription = null) }, // the label speaks
        label = { Text(stringResource(tabLabel(tab))) },
        modifier = Modifier.testTag(appTabTag(tab)),
      )
    }
  }
}

@Composable
private fun TabColumn(padding: PaddingValues, content: @Composable ColumnScope.() -> Unit) {
  Column(
    modifier =
      Modifier.fillMaxSize()
        .padding(padding)
        .consumeWindowInsets(padding)
        .imePadding()
        .verticalScroll(rememberScrollState())
        .padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
    content = content,
  )
}

/** The battery widget: its preview, "Add to home screen", then its own sections. */
@Composable
private fun BatteryTab(
  state: MainUiState,
  overview: BatteryOverview,
  widget: WidgetModel,
  actions: MainActions,
  previewRequest: PreviewRequest,
) {
  val settings = state.settings
  WidgetPreviewCard(widget, settings.fontPercents, settings.appearance.theme, previewRequest)
  AddWidgetRow(
    R.string.action_add_widget_hint,
    onAddWidget = { actions.requestPinWidget(BatteryWidgetReceiver::class.java) },
  )
  WidgetSection(
    settings.display,
    settings.fontPercents,
    settings.textSizeChanged,
    actions.saveDisplay,
    actions.saveFontPercents,
    actions.resetBatterySizes,
  )
  ColorsSection(
    settings.colorLevels,
    LevelBasis.of(overview.reading, settings),
    state.errors,
    actions.saveLevels,
    actions.useDefaults,
    values = LevelValue.BATTERY,
  )
  EntitiesSection(
    settings.entities,
    state.errors,
    actions.saveEntities,
    actions.findTemperatureSensors,
    initiallyExpanded = overview.setupNeeded,
  )
  PackSection(
    settings.batteryType,
    settings.cellCount,
    settings.capacityAh,
    state.errors,
    actions.saveBatteryType,
    actions.savePack,
  )
}

/** The inverter strip: its preview, "Add to home screen", then its own sections. */
@Composable
private fun InverterTab(
  state: MainUiState,
  inverter: InverterWidgetModel,
  actions: MainActions,
  stripRequest: StripRequest,
) {
  val settings = state.settings
  val strip = settings.inverter
  InverterPreviewCard(inverter, strip.fontPercents, settings.appearance.theme, stripRequest)
  AddWidgetRow(
    R.string.action_add_strip_hint,
    onAddWidget = { actions.requestPinWidget(InverterWidgetReceiver::class.java) },
  )
  InverterDisplaySection(
    strip.modeStyle,
    strip.fontPercents,
    actions.saveModeStyle,
    actions.saveInverterFonts,
    actions.resetInverterSizes,
  )
  InverterIconsSection(strip, actions.saveInverterIcon)
  ColorsSection(
    settings.colorLevels,
    // The grid and output edges are fixed volts: no capacity or cell count.
    LevelBasis(null, null),
    state.errors,
    actions.saveLevels,
    actions.useDefaults,
    values = LevelValue.INVERTER,
  )
  InverterEntitiesSection(
    strip.entities,
    state.errors,
    actions.saveInverterEntities,
    initiallyExpanded = inverter is InverterWidgetModel.Empty && inverter.kind == EmptyKind.SETUP,
  )
}

/** What both widgets share: the look, the connection, the sync, the alerts and the launcher scale. */
@Composable
private fun CommonTab(state: MainUiState, actions: MainActions, language: LanguageChoice?) {
  val settings = state.settings
  AppearanceSection(settings.appearance, language, actions.saveAppearance, actions.setLanguage)
  ConnectionSection(
    settings.connection,
    state.errors,
    actions.saveConnection,
    initiallyExpanded = state.connectionMissing,
  )
  TokenSection(
    settings.connection.tokenType,
    state.hasToken,
    state.showTokenTypeHint,
    state.errors,
    actions.saveToken,
    actions.saveTokenType,
    actions.removeToken,
    initiallyExpanded = state.connectionMissing,
  )
  RefreshSection(settings.refresh, state.errors, actions.saveRefresh)
  NotificationSection(
    settings.notificationsOn,
    settings.refresh.auto,
    actions.setNotificationsOn,
    actions.openNotificationSettings,
  )
  HomeScaleSection(settings.homeScalePercent, actions.saveHomeScale, actions.resetHomeScale)
}

@StringRes
private fun tabLabel(tab: AppTab): Int =
  when (tab) {
    AppTab.BATTERY -> R.string.tab_battery
    AppTab.INVERTER -> R.string.tab_inverter
    AppTab.COMMON -> R.string.tab_common
  }

@DrawableRes
private fun tabIcon(tab: AppTab): Int =
  when (tab) {
    AppTab.BATTERY -> R.drawable.ic_battery
    AppTab.INVERTER -> R.drawable.ic_inv_electrical_services
    AppTab.COMMON -> R.drawable.ic_settings
  }
