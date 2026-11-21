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
import io.lumensync.habattery.ui.AppTabRequest
import io.lumensync.habattery.ui.MainActions
import io.lumensync.habattery.ui.MainScreen
import io.lumensync.habattery.ui.MainViewModel
import io.lumensync.habattery.ui.NamedWidget
import io.lumensync.habattery.ui.PreviewRequest
import io.lumensync.habattery.ui.PreviewTab
import io.lumensync.habattery.ui.StripRequest
import io.lumensync.habattery.ui.reportedWidgetSize
import io.lumensync.habattery.ui.theme.HaBatteryTheme
import io.lumensync.habattery.widget.BatteryWidgetReceiver
import io.lumensync.habattery.widget.EXTRA_TAPPED_WIDGET_ID
import io.lumensync.habattery.widget.InverterLayoutSpec
import kotlinx.coroutines.launch

/** The app screen. It is also both widgets' configure activity (see [confirmWidgetPlacement]). */
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

  /** The inverter card's counterpart of [previewRequest]. */
  private var stripRequest by mutableStateOf(StripRequest(InverterLayoutSpec.S3X1))

  /** The app tab to show (spec 0012); like [previewRequest], made again by [onNewIntent]. */
  private var tabRequest by mutableStateOf(AppTabRequest(null))

  override fun onCreate(savedInstanceState: Bundle?) {
    enableEdgeToEdge() // first frame: the system's mode; the effect below applies the chosen theme
    super.onCreate(savedInstanceState)
    confirmWidgetPlacement()
    val appLanguage = application.container.appLanguage
    language = if (appLanguage.supported) appLanguage.current() else null
    val actions = mainActions(appLanguage)
    val opened = openedWidget()
    previewRequest = PreviewRequest(PreviewTab.first(opened, firstPlacedSize()))
    stripRequest = StripRequest(StripRequest.named(opened) ?: InverterLayoutSpec.S3X1)
    tabRequest = AppTabRequest(opened?.provider)
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
      HaBatteryTheme(darkTheme = dark) { MainScreen(state, actions, language, previewRequest, stripRequest, tabRequest) }
    }
  }

  /**
   * A widget tap (or the launcher) while the app is open (`singleTop`: one instance). A tap names
   * its widget: the screen opens that widget's app tab, and a battery or strip widget also sets
   * that preview's size tab. Without a widget id (the launcher icon) both tabs stay.
   */
  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    confirmWidgetPlacement()
    val opened = openedWidget()
    previewRequest = previewRequest.next(PreviewTab.named(opened))
    stripRequest = stripRequest.next(StripRequest.named(opened))
    tabRequest = tabRequest.next(opened?.provider)
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

  /**
   * The widget this activity was opened for (spec 0006): the configured one, else the tapped one;
   * null when the intent names none.
   */
  private fun openedWidget(): NamedWidget? =
    (configuredWidgetId() ?: tappedWidgetId())?.let { NamedWidget(providerOf(it), reportedSize(it)) }

  /** The provider class name of widget [id]; null when Android no longer knows the widget. */
  private fun providerOf(id: Int): String? =
    AppWidgetManager.getInstance(this).getAppWidgetInfo(id)?.provider?.className

  /** The size of the first placed battery widget; null when none is placed. */
  private fun firstPlacedSize(): DpSize? =
    AppWidgetManager.getInstance(this)
      .getAppWidgetIds(ComponentName(this, BatteryWidgetReceiver::class.java))
      .firstOrNull()
      ?.let(::reportedSize)

  private fun mainActions(appLanguage: AppLanguage) =
    MainActions(
      refreshNow = viewModel::refreshNow,
      saveConnection = viewModel::saveConnection,
      saveToken = viewModel::saveToken,
      saveTokenType = viewModel::saveTokenType,
      removeToken = viewModel::removeToken,
      saveEntities = viewModel::saveEntities,
      saveInverterEntities = viewModel::saveInverterEntities,
      findTemperatureSensors = viewModel::findTemperatureSensors,
      saveRefresh = viewModel::saveRefresh,
      setNotificationsOn = viewModel::setNotificationsOn,
      saveCapacity = viewModel::saveCapacity,
      saveDisplay = viewModel::saveDisplay,
      saveFontPercents = viewModel::saveFontPercents,
      saveHomeScale = viewModel::saveHomeScale,
      resetBatterySizes = viewModel::resetBatterySizes,
      resetHomeScale = viewModel::resetHomeScale,
      saveModeStyle = viewModel::saveModeStyle,
      saveInverterFonts = viewModel::saveInverterFonts,
      resetInverterSizes = viewModel::resetInverterSizes,
      saveInverterIcon = viewModel::saveInverterIcon,
      saveAppearance = viewModel::saveAppearance,
      setLanguage = {
        language = it
        // The system recreates the activity; App.onConfigurationChanged redraws the widgets.
        appLanguage.set(it)
      },
      saveLevels = viewModel::saveLevels,
      useDefaults = viewModel::useDefaults,
      clearErrors = viewModel::clearErrors,
      requestPinWidget = { receiver ->
        lifecycleScope.launch {
          GlanceAppWidgetManager(this@MainActivity).requestPinGlanceAppWidget(receiver)
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
