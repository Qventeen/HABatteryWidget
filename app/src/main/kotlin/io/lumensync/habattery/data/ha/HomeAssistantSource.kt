package io.lumensync.habattery.data.ha

import io.lumensync.habattery.data.store.StoreJson
import io.lumensync.habattery.domain.model.ConnectionSettings
import io.lumensync.habattery.domain.model.FetchRequest
import io.lumensync.habattery.domain.model.FetchResult
import io.lumensync.habattery.domain.model.SensorList
import io.lumensync.habattery.domain.model.SyncError
import io.lumensync.habattery.domain.port.BatterySource
import java.util.concurrent.TimeUnit
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient

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
  private val http = HaHttp.create(client)

  override suspend fun fetch(
    connection: ConnectionSettings,
    request: FetchRequest,
    token: String,
  ): FetchResult =
    when (val reply = send(connection, TemplateBuilder.build(request), token)) {
      is HaReply.Failure -> FetchResult.failed(request, reply.error)
      is HaReply.Body -> parse(reply.text, request)
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
      is HaReply.Failure -> SensorList.Failed(reply.error)
      is HaReply.Body ->
        TemperatureSensors.parse(reply.text, p)?.let(SensorList::Found)
          ?: SensorList.Failed(SyncError.BadResponse)
    }
  }

  private suspend fun send(connection: ConnectionSettings, template: String, token: String): HaReply =
    http.post(
      connection,
      listOf("api", "template"),
      buildJsonObject { put("template", template) }.toString(),
      token,
    )

  private fun parse(text: String, request: FetchRequest): FetchResult {
    val response =
      try {
        StoreJson.decodeFromString<TemplateResponse>(text)
      } catch (_: SerializationException) {
        return FetchResult.failed(request, SyncError.BadResponse)
      } catch (_: IllegalArgumentException) {
        return FetchResult.failed(request, SyncError.BadResponse)
      }
    return response.toFetchResult(request, clock())
  }
}
