package io.lumensync.habattery.data.ha

import io.lumensync.habattery.data.store.StoreJson
import io.lumensync.habattery.domain.UrlPolicy
import io.lumensync.habattery.domain.model.ConnectionSettings
import io.lumensync.habattery.domain.model.EntitySettings
import io.lumensync.habattery.domain.model.SyncError
import io.lumensync.habattery.domain.model.SyncResult
import io.lumensync.habattery.domain.port.BatterySource
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

fun defaultHttpClient(): OkHttpClient =
  OkHttpClient.Builder()
    .connectTimeout(4, TimeUnit.SECONDS)
    .readTimeout(5, TimeUnit.SECONDS)
    .callTimeout(10, TimeUnit.SECONDS)
    .build()

internal fun pickError(primary: SyncError, fallback: SyncError): SyncError =
  if (primary is SyncError.Unreachable) fallback else primary

class HomeAssistantSource(
  private val client: OkHttpClient,
  private val clock: () -> Long = System::currentTimeMillis,
) : BatterySource {

  override suspend fun fetch(
    connection: ConnectionSettings,
    entities: EntitySettings,
    token: String,
  ): SyncResult {
    val primary = connection.primaryUrl
    val fallback = connection.fallbackUrl
    val urls = buildList {
      add(primary)
      if (fallback.isNotBlank() && fallback.trimEnd('/') != primary.trimEnd('/')) add(fallback)
    }
    val template = TemplateBuilder.build(entities)
    var firstError: SyncError? = null
    var lastError: SyncError = SyncError.Unreachable
    for (url in urls) {
      when (val result = fetchOne(url, template, token)) {
        is SyncResult.Success -> return result
        is SyncResult.Failure -> {
          lastError = if (firstError == null) result.error else pickError(firstError, result.error)
          firstError = lastError
        }
      }
    }
    return SyncResult.Failure(lastError)
  }

  private suspend fun fetchOne(baseUrl: String, template: String, token: String): SyncResult =
    when (UrlPolicy.check(baseUrl)) {
      UrlPolicy.Verdict.INSECURE -> SyncResult.Failure(SyncError.InsecureConnection)
      UrlPolicy.Verdict.INVALID -> SyncResult.Failure(SyncError.Unreachable)
      UrlPolicy.Verdict.OK -> withContext(Dispatchers.IO) { post(baseUrl, template, token) }
    }

  private fun post(baseUrl: String, template: String, token: String): SyncResult {
    val body =
      buildJsonObject { put("template", template) }
        .toString()
        .toRequestBody("application/json".toMediaType())
    val request =
      try {
        Request.Builder()
          .url(baseUrl.trim().trimEnd('/') + "/api/template")
          .header("Authorization", "Bearer $token")
          .post(body)
          .build()
      } catch (_: IllegalArgumentException) {
        return SyncResult.Failure(SyncError.Unreachable)
      }
    return try {
      client.newCall(request).execute().use { response ->
        when {
          response.code == 401 || response.code == 403 ->
            SyncResult.Failure(SyncError.Unauthorized)
          !response.isSuccessful -> SyncResult.Failure(SyncError.HttpError(response.code))
          else -> parse(response.body.string())
        }
      }
    } catch (_: IOException) {
      SyncResult.Failure(SyncError.Unreachable)
    }
  }

  private fun parse(text: String): SyncResult {
    val reading =
      try {
        StoreJson.decodeFromString<TemplateResponse>(text).toReading(clock())
      } catch (_: SerializationException) {
        null
      } catch (_: IllegalArgumentException) {
        null
      }
    return if (reading == null) SyncResult.Failure(SyncError.BadResponse)
    else SyncResult.Success(reading)
  }
}
