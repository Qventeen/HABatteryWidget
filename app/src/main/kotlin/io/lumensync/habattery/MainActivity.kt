package io.lumensync.habattery

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.lumensync.habattery.domain.model.LanguageChoice
import io.lumensync.habattery.system.AppLanguage
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
          toggleAuto = c.toggleAutoRefresh,
          scheduler = c.syncScheduler,
          widgets = c.widgetRefresher,
          source = c.batterySource,
        )
      }
    }
  }

  /** The language choice shown; null where the system has no per-app language (before Android 13). */
  private var language by mutableStateOf<LanguageChoice?>(null)

  override fun onCreate(savedInstanceState: Bundle?) {
    enableEdgeToEdge() // first frame: the system's mode; the effect below applies the chosen theme
    super.onCreate(savedInstanceState)
    confirmWidgetPlacement()
    val appLanguage = application.container.appLanguage
    language = if (appLanguage.supported) appLanguage.current() else null
    val actions = mainActions(appLanguage)
    setContent {
      val state by viewModel.uiState.collectAsStateWithLifecycle()
      val dark = state.settings.appearance.theme.isDark(isSystemInDarkTheme())
      // The bars follow the chosen theme, not only the system's.
      DisposableEffect(dark) {
        enableEdgeToEdge(
          statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
          navigationBarStyle = SystemBarStyle.auto(LIGHT_SCRIM, DARK_SCRIM) { dark },
        )
        onDispose {}
      }
      HaBatteryTheme(darkTheme = dark) { MainScreen(state, actions, language) }
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

  private fun mainActions(appLanguage: AppLanguage) =
    MainActions(
      refreshNow = viewModel::refreshNow,
      toggleAutoRefresh = viewModel::toggleAutoRefresh,
      saveConnection = viewModel::saveConnection,
      saveToken = viewModel::saveToken,
      saveEntities = viewModel::saveEntities,
      findTemperatureSensors = viewModel::findTemperatureSensors,
      saveRefresh = viewModel::saveRefresh,
      setNotifyOnFull = viewModel::setNotifyOnFull,
      saveCapacity = viewModel::saveCapacity,
      saveDisplay = viewModel::saveDisplay,
      saveFontSizes = viewModel::saveFontSizes,
      saveAppearance = viewModel::saveAppearance,
      setLanguage = {
        language = it
        // The system recreates the activity; App.onConfigurationChanged redraws the widgets.
        appLanguage.set(it)
      },
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

  private companion object {
    // The scrims enableEdgeToEdge uses for the 3-button navigation bar by default.
    val LIGHT_SCRIM = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
    val DARK_SCRIM = Color.argb(0x80, 0x1b, 0x1b, 0x1b)
  }
}
