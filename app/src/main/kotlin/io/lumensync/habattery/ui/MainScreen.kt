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
import io.lumensync.habattery.domain.model.EntitySettings
import io.lumensync.habattery.ui.sections.ActionsRow
import io.lumensync.habattery.ui.sections.CapacitySection
import io.lumensync.habattery.ui.sections.ConnectionSection
import io.lumensync.habattery.ui.sections.EntitiesSection
import io.lumensync.habattery.ui.sections.NotificationSection
import io.lumensync.habattery.ui.sections.OverviewCard
import io.lumensync.habattery.ui.sections.RefreshSection
import io.lumensync.habattery.ui.sections.TokenSection
import java.util.Locale

/** What the screen can ask for: the ViewModel's functions plus two system actions. */
data class MainActions(
  val refreshNow: () -> Unit,
  val saveConnection: (primary: String, fallback: String) -> Unit,
  val saveToken: (token: String) -> Unit,
  val saveEntities: (EntitySettings) -> Unit,
  val saveRefresh: (auto: Boolean, intervalText: String) -> Unit,
  val setNotifyOnFull: (Boolean) -> Unit,
  val saveCapacity: (text: String, locale: Locale) -> Unit,
  val requestPinWidget: () -> Unit,
  val openNotificationSettings: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(state: MainUiState, actions: MainActions) {
  Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.app_name)) }) }) { padding ->
    val overview = state.overview
    if (overview == null) {
      // The first state from storage has not arrived yet; drafts must start from real settings.
      Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
      }
    } else {
      MainContent(state, overview, actions, padding)
    }
  }
}

@Composable
private fun MainContent(
  state: MainUiState,
  overview: BatteryOverview,
  actions: MainActions,
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
    OverviewCard(overview)
    ActionsRow(state.syncing, actions.refreshNow, actions.requestPinWidget)
    ConnectionSection(settings.connection, state.errors, actions.saveConnection)
    TokenSection(state.hasToken, state.errors, actions.saveToken)
    EntitiesSection(settings.entities, state.errors, actions.saveEntities)
    RefreshSection(settings.refresh, state.errors, actions.saveRefresh)
    NotificationSection(
      settings.notifyOnFull,
      actions.setNotifyOnFull,
      actions.openNotificationSettings,
    )
    CapacitySection(settings.capacityAh, state.errors, actions.saveCapacity)
  }
}
