package io.lumensync.habattery.data.secure

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/** The encrypted token is useless on another device (its key stays in this Keystore): never back it up. */
class BackupRulesTest {
  private val tokenPath = "datastore/$SECURE_FILE"

  /** The `path` of every `<exclude>` inside [section] (or anywhere, when null). */
  private fun excludedPaths(file: String, section: String? = null): List<String> {
    val xml = File("src/main/res/xml", file)
    assertTrue(xml.absolutePath, xml.isFile)
    val root = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(xml).documentElement
    val scope = if (section == null) root else root.getElementsByTagName(section).item(0) as Element
    val excludes = scope.getElementsByTagName("exclude")
    return (0 until excludes.length).map { (excludes.item(it) as Element).getAttribute("path") }
  }

  @Test
  fun fullBackup_excludesTokenFile() =
    assertTrue(tokenPath in excludedPaths("backup_rules.xml"))

  @Test
  fun dataExtraction_excludesTokenFileFromCloudAndDeviceTransfer() =
    listOf("cloud-backup", "device-transfer").forEach { section ->
      assertTrue(section, tokenPath in excludedPaths("data_extraction_rules.xml", section))
    }
}
