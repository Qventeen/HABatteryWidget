package io.lumensync.habattery.domain.model

/** The app language setting. The system keeps it (spec 0008, approach C1); it is not in [Settings]. */
enum class LanguageChoice(val tag: String) {
  SYSTEM(""),
  ENGLISH("en"),
  UKRAINIAN("uk");

  companion object {
    /** The choice for the app's locale tags ("" or null: none set, so System). Unknown: System. */
    fun fromTag(tag: String?): LanguageChoice {
      val language = tag.orEmpty().substringBefore(',').substringBefore('-').substringBefore('_')
      return entries.firstOrNull { it != SYSTEM && it.tag == language } ?: SYSTEM
    }
  }
}
