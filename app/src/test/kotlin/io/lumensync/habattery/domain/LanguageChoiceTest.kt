package io.lumensync.habattery.domain

import io.lumensync.habattery.domain.model.LanguageChoice
import org.junit.Assert.assertEquals
import org.junit.Test

class LanguageChoiceTest {
  @Test
  fun noTag_isSystem() {
    assertEquals(LanguageChoice.SYSTEM, LanguageChoice.fromTag(""))
    assertEquals(LanguageChoice.SYSTEM, LanguageChoice.fromTag(null))
  }

  @Test
  fun englishAndUkrainian_withOrWithoutRegion() {
    assertEquals(LanguageChoice.ENGLISH, LanguageChoice.fromTag("en"))
    assertEquals(LanguageChoice.ENGLISH, LanguageChoice.fromTag("en-US"))
    assertEquals(LanguageChoice.UKRAINIAN, LanguageChoice.fromTag("uk"))
    assertEquals(LanguageChoice.UKRAINIAN, LanguageChoice.fromTag("uk-UA"))
  }

  @Test
  fun aList_usesTheFirst() {
    assertEquals(LanguageChoice.UKRAINIAN, LanguageChoice.fromTag("uk-UA,en"))
  }

  @Test
  fun otherLanguage_isSystem() {
    assertEquals(LanguageChoice.SYSTEM, LanguageChoice.fromTag("de"))
  }
}
