package io.lumensync.habattery

import android.content.Context
import android.util.Log
import androidx.datastore.dataStoreFile
import androidx.glance.appwidget.GlanceAppWidgetManager
import io.lumensync.habattery.data.cache.DataStoreReadingCache
import io.lumensync.habattery.data.ha.HomeAssistantSource
import io.lumensync.habattery.data.ha.StatesSource
import io.lumensync.habattery.data.ha.TokenTypeSource
import io.lumensync.habattery.data.ha.defaultHttpClient
import io.lumensync.habattery.data.secure.EncryptedTokenStore
import io.lumensync.habattery.data.secure.KeystoreCrypto
import io.lumensync.habattery.data.secure.SECURE_FILE
import io.lumensync.habattery.data.settings.DataStoreSettingsRepository
import io.lumensync.habattery.data.store.CacheRecord
import io.lumensync.habattery.data.store.JsonSerializer
import io.lumensync.habattery.data.store.SettingsRecord
import io.lumensync.habattery.data.store.TokenRecord
import io.lumensync.habattery.data.store.jsonDataStore
import io.lumensync.habattery.data.store.settingsDataStore
import io.lumensync.habattery.domain.LevelAlert
import io.lumensync.habattery.domain.SyncBattery
import io.lumensync.habattery.domain.ToggleAutoRefresh
import io.lumensync.habattery.domain.port.BatterySource
import io.lumensync.habattery.domain.port.LevelNotifier
import io.lumensync.habattery.domain.port.ReadingCache
import io.lumensync.habattery.domain.port.SettingsRepository
import io.lumensync.habattery.domain.port.SyncScheduler
import io.lumensync.habattery.domain.port.TokenStore
import io.lumensync.habattery.domain.port.WidgetRefresher
import io.lumensync.habattery.system.AppLanguage
import io.lumensync.habattery.system.ConfigWatcher
import io.lumensync.habattery.system.TextConfig
import io.lumensync.habattery.system.WorkManagerSyncScheduler
import io.lumensync.habattery.widget.BatteryWidget
import io.lumensync.habattery.widget.GlanceWidgetRefresher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Stand-in until the Android notifier replaces it. */
private object NoLevelNotifier : LevelNotifier {
  override fun canPost(): Boolean = false

  override fun post(alert: LevelAlert) = Unit
}

/** Manual DI: builds every object once per process, so each DataStore file has one instance. */
class AppContainer(context: Context) {
  private val appContext = context.applicationContext

  /**
   * Work that must outlive a screen or a broadcast, such as re-applying the schedule. A failure
   * there is logged by class name only (a message could carry data) and never crashes the app.
   */
  val appScope: CoroutineScope =
    CoroutineScope(
      SupervisorJob() +
        Dispatchers.Default +
        CoroutineExceptionHandler { _, e -> Log.w(TAG, e.javaClass.name) }
    )

  /** DataStore does file I/O on this scope. */
  private val storeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  val settingsRepository: SettingsRepository =
    DataStoreSettingsRepository(
      settingsDataStore(storeScope) { appContext.dataStoreFile(SETTINGS_FILE) }
    )

  val readingCache: ReadingCache =
    DataStoreReadingCache(
      jsonDataStore(JsonSerializer(CacheRecord(), CacheRecord.serializer()), storeScope) {
        appContext.dataStoreFile(CACHE_FILE)
      }
    )

  val tokenStore: TokenStore =
    EncryptedTokenStore(
      jsonDataStore(JsonSerializer(TokenRecord(), TokenRecord.serializer()), storeScope) {
        appContext.dataStoreFile(SECURE_FILE)
      },
      KeystoreCrypto(),
    )

  val widgetRefresher: WidgetRefresher = GlanceWidgetRefresher(appContext)

  val syncScheduler: SyncScheduler =
    WorkManagerSyncScheduler(appContext) {
      GlanceAppWidgetManager(appContext).getGlanceIds(BatteryWidget::class.java).isNotEmpty()
    }

  val batterySource: BatterySource = defaultHttpClient().let { TokenTypeSource(HomeAssistantSource(it), StatesSource(it)) }

  val appLanguage = AppLanguage(appContext)

  val configWatcher = ConfigWatcher(TextConfig.of(appContext.resources.configuration))

  val syncBattery: SyncBattery =
    SyncBattery(
      settings = settingsRepository,
      tokens = tokenStore,
      source = batterySource,
      cache = readingCache,
      notifier = NoLevelNotifier,
      widgets = widgetRefresher,
    )

  val toggleAutoRefresh: ToggleAutoRefresh =
    ToggleAutoRefresh(settingsRepository, syncScheduler, widgetRefresher)

  private companion object {
    const val TAG = "HaBattery"
    const val SETTINGS_FILE = "settings.json"
    const val CACHE_FILE = "reading_cache.json"
  }
}
