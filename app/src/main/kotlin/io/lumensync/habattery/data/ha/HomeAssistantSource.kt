package io.lumensync.habattery.data.ha

import io.lumensync.habattery.data.store.StoreJson
import io.lumensync.habattery.domain.UrlPolicy
import io.lumensync.habattery.domain.model.ConnectionSettings
import io.lumensync.habattery.domain.model.EntitySettings
import io.lumensync.habattery.domain.model.SensorList
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
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

fun defaultHttpClient(): OkHttpClient =
  OkHttpClient.Builder()
    .connectTimeout(4, TimeUnit.SECONDS)
    .readTimeout(5, TimeUnit.SECONDS)
    .callTimeout(10, TimeUnit.SECONDS)
    // A redirect target would skip UrlPolicy (and could take the token to plain HTTP).
    .followRedirects(false)
    .followSslRedirects(false)
    .build()

class HomeAssistantSource(
  private val client: OkHttpClient,
  private val clock: () -> Long = System::currentTimeMillis,
) : BatterySource {

  override suspend fun fetch(
    connection: ConnectionSettings,
    entities: EntitySettings,
    token: String,
  ): SyncResult =
    when (val reply = send(connection, TemplateBuilder.build(entities), token)) {
      is Reply.Failure -> SyncResult.Failure(reply.error)
      is Reply.Body -> parse(reply.text)
    }

  override suspend fun listTemperatureSensors(
    connection: ConnectionSettings,
    prefix: String,
    token: String,
  ): SensorList {
    val p = prefix.trim().trimEnd('_')
    // Only object-id characters reach the template; anything else cannot match a sensor.
    if (!TemplateBuilder.isObjectIdPrefix(p)) return SensorList.Found(emptyList())
    return when (val reply = send(connection, TemplateBuilder.temperatureSensors(p), token)) {
      is Reply.Failure -> SensorList.Failed(reply.error)
      is Reply.Body ->
        TemperatureSensors.parse(reply.text, p)?.let(SensorList::Found)
          ?: SensorList.Failed(SyncError.BadResponse)
    }
  }

  private sealed interface Reply {
    data class Body(val text: String) : Reply

    data class Failure(val error: SyncError) : Reply
  }

  private suspend fun send(connection: ConnectionSettings, template: String, token: String): Reply =
    when (UrlPolicy.check(connection.url)) {
      UrlPolicy.Verdict.INVALID -> Reply.Failure(SyncError.Unreachable)
      // PUBLIC_HTTP is allowed: the settings screen warns, and the user decides.
      UrlPolicy.Verdict.OK,
      UrlPolicy.Verdict.PUBLIC_HTTP ->
        withContext(Dispatchers.IO) { post(connection.url, template, token) }
    }

  private fun post(baseUrl: String, template: String, token: String): Reply {
    val body =
      buildJsonObject { put("template", template) }
        .toString()
        .toRequestBody("application/json".toMediaType())
    val url =
      (baseUrl.trim().trimEnd('/') + "/api/template").toHttpUrlOrNull()
        ?: return Reply.Failure(SyncError.Unreachable)
    val request =
      try {
        Request.Builder().url(url).header("Authorization", "Bearer $token").post(body).build()
      } catch (_: IllegalArgumentException) {
        // OkHttp refuses control or non-ASCII characters in a header: no server accepts this token.
        return Reply.Failure(SyncError.Unauthorized)
      }
    return try {
      client.newCall(request).execute().use { response ->
        when {
          response.code == 401 || response.code == 403 -> Reply.Failure(SyncError.Unauthorized)
          !response.isSuccessful -> Reply.Failure(SyncError.HttpError(response.code))
          else -> Reply.Body(response.body.string())
        }
      }
    } catch (_: IOException) {
      Reply.Failure(SyncError.Unreachable)
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
