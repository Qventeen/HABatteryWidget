package io.lumensync.habattery.system

import android.app.LocaleManager
import android.content.Context
import android.os.Build
import android.os.LocaleList
import androidx.annotation.RequiresApi
import io.lumensync.habattery.domain.model.LanguageChoice

/**
 * The app's language through the system's per-app language (Android 13+). The system stores the
 * choice and recreates the activity; the widget's texts come from the app's resources, so they
 * follow. Before Android 13 there is no setting: [supported] is false.
 */
class AppLanguage(private val context: Context) {
  val supported: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

  fun current(): LanguageChoice {
    if (!supported) return LanguageChoice.SYSTEM
    return LanguageChoice.fromTag(manager().applicationLocales.toLanguageTags())
  }

  fun set(choice: LanguageChoice) {
    if (!supported) return
    manager().applicationLocales = LocaleList.forLanguageTags(choice.tag)
  }

  @RequiresApi(Build.VERSION_CODES.TIRAMISU)
  private fun manager() = context.getSystemService(LocaleManager::class.java)
}
