package io.lumensync.habattery.data.settings

import io.lumensync.habattery.data.store.JsonSerializer
import io.lumensync.habattery.data.store.SettingsRecord
import io.lumensync.habattery.data.store.jsonDataStore
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
      jsonDataStore(
        JsonSerializer(SettingsRecord(), SettingsRecord.serializer()),
        backgroundScope,
        { file },
      )
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
    r.update { it.copy(notifyOnFull = true) }
    assertTrue(r.settings.first().notifyOnFull)
    assertTrue(file.readText().contains("\"schemaVersion\":1"))
  }

  @Test
  fun unknownFields_areIgnored() = runTest {
    file.writeText(
      """{"schemaVersion":1,"future":"x","settings":{"notifyOnFull":true,"newField":3}}"""
    )
    assertTrue(repo().settings.first().notifyOnFull)
  }

  @Test
  fun fontPercents_areClampedWhenRead() = runTest {
    file.writeText("""{"schemaVersion":1,"settings":{"fontPercents":{"soc":5,"small":150}}}""")
    val percents = repo().settings.first().fontPercents
    assertEquals(25, percents.soc)
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
    assertEquals(150, repo().settings.first().homeScalePercent)
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
