package io.lumensync.habattery

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.lumensync.habattery.ui.MainActions
import io.lumensync.habattery.ui.MainScreen
import io.lumensync.habattery.ui.MainViewModel
import io.lumensync.habattery.ui.theme.HaBatteryTheme
import io.lumensync.habattery.widget.BatteryWidgetReceiver
import kotlinx.coroutines.launch

/** The app screen. It is also the widget's configure activity (see [confirmWidgetPlacement]). */
class MainActivity : ComponentActivity() {
  private val viewModel: MainViewModel by viewModels {
    viewModelFactory {
      initializer {
        val c = application.container
        MainViewModel(
          settings = c.settingsRepository,
          tokens = c.tokenStore,
          cache = c.readingCache,
          sync = c.syncBattery,
          scheduler = c.syncScheduler,
          widgets = c.widgetRefresher,
        )
      }
    }
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    enableEdgeToEdge()
    super.onCreate(savedInstanceState)
    confirmWidgetPlacement()
    val actions = mainActions()
    setContent {
      HaBatteryTheme {
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        MainScreen(state, actions)
      }
    }
  }

  /**
   * On API 26–30 the launcher opens this activity when the user places the widget, and removes the
   * widget unless the result is OK. Nothing needs configuring per widget, so confirm at once.
   */
  private fun confirmWidgetPlacement() {
    val id =
      intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
    if (id != AppWidgetManager.INVALID_APPWIDGET_ID) {
      setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
    }
  }

  private fun mainActions() =
    MainActions(
      refreshNow = viewModel::refreshNow,
      toggleAutoRefresh = viewModel::toggleAutoRefresh,
      saveConnection = viewModel::saveConnection,
      saveToken = viewModel::saveToken,
      saveEntities = viewModel::saveEntities,
      saveRefresh = viewModel::saveRefresh,
      setNotifyOnFull = viewModel::setNotifyOnFull,
      saveCapacity = viewModel::saveCapacity,
      requestPinWidget = {
        lifecycleScope.launch {
          GlanceAppWidgetManager(this@MainActivity)
            .requestPinGlanceAppWidget(BatteryWidgetReceiver::class.java)
        }
      },
      openNotificationSettings = {
        startActivity(
          Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        )
      },
    )
}
