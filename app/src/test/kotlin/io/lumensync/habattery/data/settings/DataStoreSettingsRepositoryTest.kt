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
  fun fontSizes_areClampedWhenRead() = runTest {
    file.writeText("""{"schemaVersion":1,"settings":{"fontSizes":{"soc":99.0,"small":1.0}}}""")
    val sizes = repo().settings.first().fontSizes
    assertEquals(42f, sizes.soc)
    assertEquals(9f, sizes.small)
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
