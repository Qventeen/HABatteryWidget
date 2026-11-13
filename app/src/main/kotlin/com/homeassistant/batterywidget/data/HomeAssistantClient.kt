package com.homeassistant.batterywidget.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/** Fetches live battery metrics from Home Assistant's `/api/template` endpoint. */
object HomeAssistantClient {

    private fun quoteJinjaString(value: String): String {
        val escaped = value
            .replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\r", "")
            .replace("\n", "")
        return "'$escaped'"
    }

    private fun floatStateExpr(entityId: String, defaultNum: Int): String =
        if (entityId.isNotBlank()) {
            "states(${quoteJinjaString(entityId)}) | float($defaultNum)"
        } else {
            defaultNum.toString()
        }

    internal fun buildJinjaPayload(entities: HaEntitiesConfig): String {
        val norm = entities.normalized()
        val powerSetup = StringBuilder()
        val powerExpr = when {
            norm.powerEntity.isNotBlank() && norm.fallbackPowerEntity.isNotBlank() -> {
                powerSetup.appendLine("{% set p_primary = states(${quoteJinjaString(norm.powerEntity)}) %}")
                powerSetup.appendLine(
                    "{% set p = p_primary if p_primary not in ['unknown', 'unavailable', 'none', ''] else states(${quoteJinjaString(norm.fallbackPowerEntity)}) %}",
                )
                "p | float(0)"
            }
            norm.powerEntity.isNotBlank() -> "states(${quoteJinjaString(norm.powerEntity)}) | float(0)"
            norm.fallbackPowerEntity.isNotBlank() -> "states(${quoteJinjaString(norm.fallbackPowerEntity)}) | float(0)"
            else -> "0"
        }

        val socExpr = if (norm.socEntity.isNotBlank()) {
            "states(${quoteJinjaString(norm.socEntity)}) | float(-1)"
        } else {
            "-1"
        }
        val currentExpr = floatStateExpr(norm.currentEntity, 0)
        val totalVExpr = floatStateExpr(norm.totalVoltageEntity, 0)
        val deltaVExpr = floatStateExpr(norm.deltaVoltageEntity, 0)
        val minCellExpr = if (norm.minCellEntity.isNotBlank()) {
            "states(${quoteJinjaString(norm.minCellEntity)}) | float(0) | int"
        } else {
            "0"
        }
        val maxCellExpr = if (norm.maxCellEntity.isNotBlank()) {
            "states(${quoteJinjaString(norm.maxCellEntity)}) | float(0) | int"
        } else {
            "0"
        }
        val timeStatusExpr = if (norm.timeStatusEntity.isNotBlank()) {
            "states(${quoteJinjaString(norm.timeStatusEntity)})"
        } else {
            "\"\""
        }
        val cellsBlock = if (norm.cellEntities.isEmpty()) {
            "[]"
        } else {
            norm.cellEntities.joinToString(
                separator = ",\n    ",
                prefix = "[\n    ",
                postfix = "\n  ]",
            ) { cellEntity ->
                "states(${quoteJinjaString(cellEntity)}) | float(0)"
            }
        }

        return buildString {
            append(powerSetup)
            append(
                """
                {{ {
                  "soc": $socExpr,
                  "power": $powerExpr,
                  "current": $currentExpr,
                  "total_v": $totalVExpr,
                  "delta_v": $deltaVExpr,
                  "min_cell": $minCellExpr,
                  "max_cell": $maxCellExpr,
                  "time_status": $timeStatusExpr,
                  "cells": $cellsBlock
                } | to_json }}
                """.trimIndent(),
            )
        }
    }

    suspend fun fetchBatterySnapshot(
        config: HaConnectionConfig,
        entities: HaEntitiesConfig = HaEntitiesConfig(),
    ): Result<BatterySnapshot> =
        withContext(Dispatchers.IO) {
            val urls = buildList {
                if (config.primaryUrl.isNotBlank()) add(config.primaryUrl.trimEnd('/'))
                val fb = config.fallbackUrl.trimEnd('/')
                if (fb.isNotBlank() && fb != config.primaryUrl.trimEnd('/')) add(fb)
            }
            if (urls.isEmpty()) {
                return@withContext Result.failure(IllegalArgumentException("Не вказано URL Home Assistant"))
            }

            val payload = buildJinjaPayload(entities)
            var lastError: Throwable? = null
            for (baseUrl in urls) {
                try {
                    val snapshot = requestFromUrl(baseUrl, config.accessToken, payload)
                    return@withContext Result.success(snapshot)
                } catch (t: Throwable) {
                    lastError = t
                }
            }
            Result.failure(lastError ?: IllegalStateException("Не вдалося з'єднатися з Home Assistant"))
        }

    private fun requestFromUrl(baseUrl: String, token: String, jinjaPayload: String): BatterySnapshot {
        val url = URL("$baseUrl/api/template")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 4000
            readTimeout = 5000
            doInput = true
            doOutput = true
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
        }

        try {
            val body = JSONObject().put("template", jinjaPayload).toString()
            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(body)
                writer.flush()
            }

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.let {
                BufferedReader(InputStreamReader(it, Charsets.UTF_8)).use { r -> r.readText() }
            }.orEmpty()

            if (code !in 200..299) {
                throw IllegalStateException("HTTP $code: ${text.take(120)}")
            }

            val obj = JSONObject(text)
            val arr = obj.optJSONArray("cells")
            val cells = buildList {
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        add(arr.optDouble(i, 0.0))
                    }
                }
            }
            return BatterySnapshot(
                socPercent = obj.optDouble("soc", -1.0),
                powerWatts = obj.optDouble("power", 0.0),
                currentAmps = obj.optDouble("current", 0.0),
                totalVoltage = obj.optDouble("total_v", 0.0),
                deltaVolts = obj.optDouble("delta_v", 0.0),
                minCellIndex = obj.optInt("min_cell", 0),
                maxCellIndex = obj.optInt("max_cell", 0),
                timeStatus = obj.optString("time_status", ""),
                cellVoltages = cells,
                updatedAtMillis = System.currentTimeMillis(),
                errorMessage = null,
            )
        } finally {
            conn.disconnect()
        }
    }
}
