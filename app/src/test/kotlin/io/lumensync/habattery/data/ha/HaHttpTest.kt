package io.lumensync.habattery.data.ha

import io.lumensync.habattery.domain.model.ConnectionSettings
import io.lumensync.habattery.domain.model.SyncError
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HaHttpTest {
  private val servers = mutableListOf<MockWebServer>()
  private val client =
    OkHttpClient.Builder()
      .connectTimeout(300, TimeUnit.MILLISECONDS)
      .readTimeout(5, TimeUnit.SECONDS)
      .callTimeout(1, TimeUnit.SECONDS)
      .build()
  private val http = HaHttp(client)

  @After fun tearDown() = servers.forEach { runCatching { it.close() } }

  private fun server(vararg responses: MockResponse): MockWebServer {
    val s = MockWebServer()
    responses.forEach { s.enqueue(it) }
    s.start()
    servers += s
    return s
  }

  private fun MockWebServer.base(path: String = "/"): String =
    url(path).newBuilder().host("127.0.0.1").build().toString()

  private fun ok(body: String = "{}") = MockResponse.Builder().code(200).body(body).build()

  private fun code(code: Int) = MockResponse.Builder().code(code).build()

  @Test
  fun get_sendsBearerAndPath() = runTest {
    val s = server(ok("hello"))
    val reply =
      http.get(ConnectionSettings(s.base()), listOf("api", "states", "sensor.bms_soc"), "t0ken")
    assertEquals(HaReply.Body("hello"), reply)
    val request = s.takeRequest()
    assertEquals("GET", request.method)
    assertEquals("/api/states/sensor.bms_soc", request.url.encodedPath)
    assertEquals("Bearer t0ken", request.headers["Authorization"])
  }

  @Test
  fun post_sendsJsonBodyBearerAndPath() = runTest {
    val s = server(ok())
    http.post(ConnectionSettings(s.base()), listOf("api", "template"), """{"a":1}""", "t0ken")
    val request = s.takeRequest()
    assertEquals("POST", request.method)
    assertEquals("/api/template", request.url.encodedPath)
    assertEquals("Bearer t0ken", request.headers["Authorization"])
    assertEquals("""{"a":1}""", request.body?.utf8())
  }

  @Test
  fun get_basePathIsKept() = runTest {
    val s = server(ok())
    http.get(ConnectionSettings(s.base("/ha/")), listOf("api", "states", "sensor.x"), "t")
    assertEquals("/ha/api/states/sensor.x", s.takeRequest().url.encodedPath)
  }

  @Test
  fun get_segmentIsEncoded() = runTest {
    val s = server(ok())
    http.get(ConnectionSettings(s.base()), listOf("api", "states", "a b/c"), "t")
    assertTrue(s.takeRequest().url.encodedPath.endsWith("/a%20b%2Fc"))
  }

  @Test
  fun get_404_isHttpError404() = runTest {
    val s = server(code(404))
    val reply = http.get(ConnectionSettings(s.base()), listOf("api"), "t")
    assertEquals(HaReply.Failure(SyncError.HttpError(404)), reply)
  }

  @Test
  fun get_401_isUnauthorized() = runTest {
    val s = server(code(401))
    val reply = http.get(ConnectionSettings(s.base()), listOf("api"), "t")
    assertEquals(HaReply.Failure(SyncError.Unauthorized), reply)
  }

  @Test
  fun get_closedPort_isUnreachable() = runTest {
    val s = server()
    val url = s.base()
    s.close()
    val reply = http.get(ConnectionSettings(url), listOf("api"), "t")
    assertEquals(HaReply.Failure(SyncError.Unreachable), reply)
  }

  @Test
  fun withCallTimeout_appliesLongerTimeout() = runTest {
    val slow = MockResponse.Builder().code(200).body("late").bodyDelay(2, TimeUnit.SECONDS).build()
    val s = server(slow, slow)
    val connection = ConnectionSettings(s.base())
    assertEquals(
      HaReply.Failure(SyncError.Unreachable),
      http.get(connection, listOf("api"), "t"),
    )
    assertEquals(
      HaReply.Body("late"),
      http.withCallTimeout(5).get(connection, listOf("api"), "t"),
    )
  }
}
