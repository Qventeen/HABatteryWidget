package io.lumensync.habattery.data.settings

import io.lumensync.habattery.data.store.settingsDataStore
import io.lumensync.habattery.domain.model.AppearanceSettings
import io.lumensync.habattery.domain.model.EntitySettings
import io.lumensync.habattery.domain.model.Settings
import io.lumensync.habattery.domain.model.ThemeChoice
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class DataStoreSettingsRepositoryTest {
  @get:Rule val tmp = TemporaryFolder()

  private val file: File by lazy { File(tmp.root, "settings.json") }

  private fun TestScope.repo() =
    DataStoreSettingsRepository(
      settingsDataStore(backgroundScope) { file }
    )

  @Test
  fun freshStore_hasNoDefaults() = runTest {
    val s = repo().settings.first()
    assertEquals(Settings(), s)
    assertEquals("", s.connection.url)
    assertEquals(EntitySettings(), s.entities)
    assertFalse(s.refresh.auto)
    assertNull(s.refresh.intervalMinutes)
    assertNull(s.capacityAh)
  }

  @Test
  fun update_roundTripsAndWritesSchemaVersion() = runTest {
    val r = repo()
    r.update { it.copy(notificationsOn = true) }
    assertTrue(r.settings.first().notificationsOn)
    assertTrue(file.readText().contains("\"schemaVersion\":2"))
  }

  @Test
  fun unknownFields_areIgnored() = runTest {
    file.writeText(
      """{"schemaVersion":1,"future":"x","settings":{"notifyOnFull":true,"newField":3}}"""
    )
    assertTrue(repo().settings.first().notificationsOn)
  }

  @Test
  fun v1File_migratesToV2() = runTest {
    file.writeText("""{"schemaVersion":1,"settings":{"notifyOnFull":true}}""")
    val r = repo()
    assertTrue(r.settings.first().notificationsOn)
    // DataStore runs the migration and writes at the first read, before any update.
    assertTrue(file.readText().contains("\"schemaVersion\":2"))
    r.update { it }
    val text = file.readText()
    assertTrue(text.contains("\"notificationsOn\":true"))
    assertFalse(text.contains("notifyOnFull"))
  }

  @Test
  fun v1File_withSwitchOff_loadsOff() = runTest {
    file.writeText("""{"schemaVersion":1,"settings":{"notifyOnFull":false}}""")
    assertFalse(repo().settings.first().notificationsOn)
  }

  @Test
  fun fontPercents_areClampedWhenRead() = runTest {
    file.writeText("""{"schemaVersion":1,"settings":{"fontPercents":{"soc":5,"small":150}}}""")
    val percents = repo().settings.first().fontPercents
    assertEquals(10, percents.soc)
    assertEquals(100, percents.small)
    assertEquals(50, percents.main)
  }

  @Test
  fun homeScale_defaultsTo100_andRoundTrips() = runTest {
    file.writeText("""{"schemaVersion":1,"settings":{"notifyOnFull":true}}""")
    val r = repo()
    assertEquals(100, r.settings.first().homeScalePercent)
    r.update { it.copy(homeScalePercent = 128) }
    assertEquals(128, r.settings.first().homeScalePercent)
  }

  @Test
  fun homeScale_aboveTheRange_isClampedWhenRead() = runTest {
    file.writeText("""{"schemaVersion":1,"settings":{"homeScalePercent":300}}""")
    assertEquals(200, repo().settings.first().homeScalePercent)
  }

  @Test
  fun homeScale_belowTheRange_isClampedWhenRead() = runTest {
    file.writeText("""{"schemaVersion":1,"settings":{"homeScalePercent":10}}""")
    assertEquals(80, repo().settings.first().homeScalePercent)
  }

  @Test
  fun oldFileWithoutAppearance_loadsTheSystemTheme() = runTest {
    file.writeText("""{"schemaVersion":1,"settings":{"notifyOnFull":true}}""")
    assertEquals(ThemeChoice.SYSTEM, repo().settings.first().appearance.theme)
  }

  @Test
  fun theme_roundTrips() = runTest {
    val r = repo()
    r.update { it.copy(appearance = AppearanceSettings(ThemeChoice.DARK)) }
    assertEquals(ThemeChoice.DARK, r.settings.first().appearance.theme)
  }

  @Test
  fun corruptFile_givesDefaults() = runTest {
    file.writeText("not json")
    assertEquals(Settings(), repo().settings.first())
  }
}
