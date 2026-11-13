package com.homeassistant.batterywidget.data

import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertNull
import junit.framework.TestCase.assertTrue
import org.junit.Test

class HomeAssistantEntitiesTest {

    @Test
    fun validate_acceptsDefaultsAndOptionalBlanks_rejectsMalformed() {
        assertNull(HaEntitiesConfig.validate(HaEntitiesConfig()))

        val minimal = HaEntitiesConfig(
            socEntity = " sensor.my_battery_soc ",
            powerEntity = "",
            fallbackPowerEntity = "",
            currentEntity = "",
            totalVoltageEntity = "",
            deltaVoltageEntity = "",
            minCellEntity = "",
            maxCellEntity = "",
            timeStatusEntity = "",
            cellEntities = listOf(" ", "sensor.cell_1", ""),
        )
        assertNull(HaEntitiesConfig.validate(minimal))
        val normalized = minimal.normalized()
        assertEquals("sensor.my_battery_soc", normalized.socEntity)
        assertEquals(listOf("sensor.cell_1"), normalized.cellEntities)

        val missingSoc = minimal.copy(socEntity = "")
        assertTrue(HaEntitiesConfig.validate(missingSoc)?.startsWith("Помилка:") == true)

        val invalidCell = HaEntitiesConfig(cellEntities = listOf("not_a_domain_entity"))
        assertTrue(HaEntitiesConfig.validate(invalidCell)?.startsWith("Помилка:") == true)
    }

    @Test
    fun buildJinjaPayload_rendersCustomAndOptionalEntities() {
        val custom = HaEntitiesConfig(
            socEntity = "sensor.pack_soc",
            powerEntity = "sensor.pack_power",
            fallbackPowerEntity = "",
            currentEntity = "sensor.pack_current",
            totalVoltageEntity = "sensor.pack_voltage",
            deltaVoltageEntity = "",
            minCellEntity = "",
            maxCellEntity = "",
            timeStatusEntity = "",
            cellEntities = emptyList(),
        )
        val payload = HomeAssistantClient.buildJinjaPayload(custom)
        assertTrue(payload.contains("states('sensor.pack_soc') | float(-1)"))
        assertTrue(payload.contains("states('sensor.pack_power') | float(0)"))
        assertFalse(payload.contains("sensor.espjkbms_espjkbms_power"))
        assertTrue(payload.contains("\"delta_v\": 0"))
        assertTrue(payload.contains("\"time_status\": \"\""))
        assertTrue(payload.contains("\"cells\": []"))
    }

    @Test
    fun batterySnapshot_withoutCellsIsValidWhenSocNonNegative() {
        val noCells = BatterySnapshot(
            socPercent = 76.0,
            powerWatts = -150.0,
            currentAmps = -5.5,
            totalVoltage = 26.2,
            deltaVolts = 0.0,
            minCellIndex = 0,
            maxCellIndex = 0,
            timeStatus = "",
            cellVoltages = emptyList(),
            updatedAtMillis = 1794300000000L,
        )
        assertTrue(noCells.hasValidData)
    }
}
