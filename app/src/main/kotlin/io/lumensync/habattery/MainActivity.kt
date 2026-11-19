package io.lumensync.habattery

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.content.res.Configuration
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
import androidx.compose.ui.unit.DpSize
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
import io.lumensync.habattery.ui.PreviewRequest
import io.lumensync.habattery.ui.PreviewTab
import io.lumensync.habattery.ui.reportedWidgetSize
import io.lumensync.habattery.ui.theme.HaBatteryTheme
import io.lumensync.habattery.widget.BatteryWidgetReceiver
import io.lumensync.habattery.widget.EXTRA_TAPPED_WIDGET_ID
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
          source = c.batterySource,
        )
      }
    }
  }

  /** The language choice shown; null where the system has no per-app language (before Android 13). */
  private var language by mutableStateOf<LanguageChoice?>(null)

  /**
   * What the preview card shows (spec 0006, B1). Made again with a higher count by [onNewIntent];
   * a new activity (rotation, language) starts at count 0, so the card keeps the user's own tab.
   */
  private var previewRequest by mutableStateOf(PreviewRequest(PreviewTab.TAB_4X2))

  override fun onCreate(savedInstanceState: Bundle?) {
    enableEdgeToEdge() // first frame: the system's mode; the effect below applies the chosen theme
    super.onCreate(savedInstanceState)
    confirmWidgetPlacement()
    val appLanguage = application.container.appLanguage
    language = if (appLanguage.supported) appLanguage.current() else null
    val actions = mainActions(appLanguage)
    previewRequest = PreviewRequest(firstPreviewTab())
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
      HaBatteryTheme(darkTheme = dark) { MainScreen(state, actions, language, previewRequest) }
    }
  }

  /**
   * A widget tap (or the launcher) while the app is open (`singleTop`: one instance). A tap names
   * its widget: the preview switches to that widget's tab. Without a widget id (the launcher
   * icon) the tab stays.
   */
  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    confirmWidgetPlacement()
    previewRequest = previewRequest.next(namedPreviewTab())
  }

  /**
   * On API 26–30 the launcher opens this activity when the user places the widget, and removes the
   * widget unless the result is OK. Nothing needs configuring per widget, so confirm at once.
   */
  private fun confirmWidgetPlacement() {
    val id = configuredWidgetId() ?: return
    setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
  }

  /** The widget this activity was opened for: placement on API 26–30, reconfigure on 31+. */
  private fun configuredWidgetId(): Int? = widgetIdExtra(AppWidgetManager.EXTRA_APPWIDGET_ID)

  /** The widget the user tapped to open the app (see [EXTRA_TAPPED_WIDGET_ID]). */
  private fun tappedWidgetId(): Int? = widgetIdExtra(EXTRA_TAPPED_WIDGET_ID)

  private fun widgetIdExtra(name: String): Int? =
    intent.getIntExtra(name, AppWidgetManager.INVALID_APPWIDGET_ID).takeIf { it != AppWidgetManager.INVALID_APPWIDGET_ID }

  /**
   * The size Android reports for widget [id], for the current orientation, as the widget options
   * give it (portrait: min width × max height).
   */
  private fun reportedSize(id: Int): DpSize {
    val options = AppWidgetManager.getInstance(this).getAppWidgetOptions(id)
    val portrait = resources.configuration.orientation != Configuration.ORIENTATION_LANDSCAPE
    return reportedWidgetSize(options, portrait)
  }

  /** The tab of the configured widget, else of the tapped one; null when the intent names none. */
  private fun namedPreviewTab(): PreviewTab? =
    PreviewTab.named(configuredWidgetId()?.let(::reportedSize), tappedWidgetId()?.let(::reportedSize))

  /**
   * The preview's first tab (spec 0006, B1): the configured widget, else the tapped one, else the
   * first placed widget, else 4×2.
   */
  private fun firstPreviewTab(): PreviewTab {
    val firstPlaced =
      AppWidgetManager.getInstance(this)
        .getAppWidgetIds(ComponentName(this, BatteryWidgetReceiver::class.java))
        .firstOrNull()
    return PreviewTab.first(
      configuredWidgetId()?.let(::reportedSize),
      tappedWidgetId()?.let(::reportedSize),
      firstPlaced?.let(::reportedSize),
    )
  }

  private fun mainActions(appLanguage: AppLanguage) =
    MainActions(
      refreshNow = viewModel::refreshNow,
      saveConnection = viewModel::saveConnection,
      saveToken = viewModel::saveToken,
      saveTokenType = viewModel::saveTokenType,
      removeToken = viewModel::removeToken,
      saveEntities = viewModel::saveEntities,
      findTemperatureSensors = viewModel::findTemperatureSensors,
      saveRefresh = viewModel::saveRefresh,
      setNotifyOnFull = viewModel::setNotifyOnFull,
      saveCapacity = viewModel::saveCapacity,
      saveDisplay = viewModel::saveDisplay,
      saveFontPercents = viewModel::saveFontPercents,
      saveHomeScale = viewModel::saveHomeScale,
      resetWidgetSettings = viewModel::resetWidgetSettings,
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
