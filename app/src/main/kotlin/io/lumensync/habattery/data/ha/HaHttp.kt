package io.lumensync.habattery.data.ha

import io.lumensync.habattery.domain.UrlPolicy
import io.lumensync.habattery.domain.model.ConnectionSettings
import io.lumensync.habattery.domain.model.SyncError
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

internal sealed interface HaReply {
  data class Body(val text: String) : HaReply

  data class Failure(val error: SyncError) : HaReply
}

/** One request to Home Assistant. The token goes only in the `Authorization` header. */
internal class HaHttp(private val client: OkHttpClient) {

  /** Same connection pool and other limits; only the whole-call timeout changes. */
  fun withCallTimeout(seconds: Long): HaHttp =
    HaHttp(client.newBuilder().callTimeout(seconds, TimeUnit.SECONDS).build())

  /** [path] holds raw segments: each one is encoded and added after the base URL's own path. */
  suspend fun get(connection: ConnectionSettings, path: List<String>, token: String): HaReply =
    send(connection, path, token) { get() }

  suspend fun post(
    connection: ConnectionSettings,
    path: List<String>,
    json: String,
    token: String,
  ): HaReply =
    send(connection, path, token) { post(json.toRequestBody("application/json".toMediaType())) }

  private suspend fun send(
    connection: ConnectionSettings,
    path: List<String>,
    token: String,
    method: Request.Builder.() -> Request.Builder,
  ): HaReply =
    when (UrlPolicy.check(connection.url)) {
      UrlPolicy.Verdict.INVALID -> HaReply.Failure(SyncError.Unreachable)
      // PUBLIC_HTTP is allowed: the settings screen warns, and the user decides.
      UrlPolicy.Verdict.OK,
      UrlPolicy.Verdict.PUBLIC_HTTP -> execute(connection.url, path, token, method)
    }

  private suspend fun execute(
    baseUrl: String,
    path: List<String>,
    token: String,
    method: Request.Builder.() -> Request.Builder,
  ): HaReply {
    val base =
      baseUrl.trim().trimEnd('/').toHttpUrlOrNull()
        ?: return HaReply.Failure(SyncError.Unreachable)
    // addPathSegment replaces the empty last segment, so a base without a path gives "/api/...".
    val url = base.newBuilder().apply { path.forEach(::addPathSegment) }.build()
    val request =
      try {
        Request.Builder().url(url).header("Authorization", "Bearer $token").method().build()
      } catch (_: IllegalArgumentException) {
        // OkHttp refuses control or non-ASCII characters in a header: no server accepts this token.
        return HaReply.Failure(SyncError.Unauthorized)
      }
    return client.newCall(request).await()
  }

  /**
   * Runs the call on OkHttp's threads. Cancelling the coroutine cancels the call, so a running
   * request stops at once instead of holding its caller until the call timeout.
   */
  private suspend fun Call.await(): HaReply =
    suspendCancellableCoroutine { continuation ->
      continuation.invokeOnCancellation { cancel() }
      enqueue(
        object : Callback {
          override fun onFailure(call: Call, e: IOException) =
            continuation.resume(HaReply.Failure(SyncError.Unreachable))

          // Any other exception goes to the caller: thrown here, it would leave the caller waiting.
          override fun onResponse(call: Call, response: Response) =
            continuation.resumeWith(runCatching { reply(response) })
        }
      )
    }

  private fun reply(response: Response): HaReply =
    try {
      response.use {
        when {
          response.code == 401 || response.code == 403 -> HaReply.Failure(SyncError.Unauthorized)
          !response.isSuccessful -> HaReply.Failure(SyncError.HttpError(response.code))
          else -> HaReply.Body(response.body.string())
        }
      }
    } catch (_: IOException) {
      HaReply.Failure(SyncError.Unreachable)
    }
}
