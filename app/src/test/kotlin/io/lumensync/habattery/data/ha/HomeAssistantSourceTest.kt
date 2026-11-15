package io.lumensync.habattery.data.ha

import io.lumensync.habattery.domain.model.BatteryReading
import io.lumensync.habattery.domain.model.ConnectionSettings
import io.lumensync.habattery.domain.model.EntitySettings
import io.lumensync.habattery.domain.model.SyncError
import io.lumensync.habattery.domain.model.SyncResult
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeAssistantSourceTest {
  private val servers = mutableListOf<MockWebServer>()
  // The app's client with short timeouts, so the tests keep its other rules (no redirects).
  private val client =
    defaultHttpClient()
      .newBuilder()
      .connectTimeout(300, TimeUnit.MILLISECONDS)
      .readTimeout(300, TimeUnit.MILLISECONDS)
      .callTimeout(2, TimeUnit.SECONDS)
      .build()
  private val source = HomeAssistantSource(client) { 1234L }
  private val entities = EntitySettings(charge = "sensor.soc")
  private val okBody =
    """{"soc":55.0,"power":-500.0,"current":-10.0,"total_v":26.5,"cells":[3.31,null,3.30]}"""

  @After fun tearDown() = servers.forEach { runCatching { it.close() } }

  private fun server(vararg responses: MockResponse): MockWebServer {
    val s = MockWebServer()
    responses.forEach { s.enqueue(it) }
    s.start()
    servers += s
    return s
  }

  private fun MockWebServer.base(): String =
    url("/").newBuilder().host("127.0.0.1").build().toString()

  private fun closedUrl(): String {
    val s = server()
    val u = s.base()
    s.close()
    return u
  }

  private fun reply(code: Int, body: String = "") =
    MockResponse.Builder().code(code).body(body).build()

  private suspend fun fetch(primary: String, fallback: String = "", token: String = "t0ken"): SyncResult =
    source.fetch(ConnectionSettings(primary, fallback), entities, token)

  private fun SyncResult.error(): SyncError = (this as SyncResult.Failure).error

  @Test
  fun success_parsesAndSendsRequest() = runTest {
    val s = server(reply(200, okBody))
    val result = fetch(s.base())
    assertEquals(
      SyncResult.Success(BatteryReading(55.0, -500.0, -10.0, 26.5, listOf(3.31, null, 3.30), updatedAtMillis = 1234L)),
      result,
    )
    val req = s.takeRequest()
    assertEquals("POST", req.method)
    assertEquals("/api/template", req.url.encodedPath)
    assertEquals("Bearer t0ken", req.headers["Authorization"])
    assertTrue(req.body!!.utf8().contains("\"template\""))
  }

  @Test
  fun status401_isUnauthorized() = runTest {
    assertEquals(SyncError.Unauthorized, fetch(server(reply(401)).base()).error())
    assertEquals(SyncError.Unauthorized, fetch(server(reply(403)).base()).error())
  }

  @Test
  fun redirect_isNotFollowed() = runTest {
    // A redirect target would skip UrlPolicy, so the source stops at the redirect.
    val target = server(reply(200, okBody))
    val redirect =
      MockResponse.Builder().code(302).addHeader("Location", target.base() + "api/template").build()
    assertEquals(SyncError.HttpError(302), fetch(server(redirect).base()).error())
    assertEquals(0, target.requestCount)
  }

  @Test
  fun tokenOkHttpCannotSend_isUnauthorized() = runTest {
    listOf("ab\u0001c", "ab\ncd", "токен").forEach { token ->
      val s = server(reply(200, okBody))
      assertEquals(token, SyncError.Unauthorized, fetch(s.base(), token = token).error())
      assertEquals(0, s.requestCount)
    }
  }

  @Test
  fun status500_isHttpError() = runTest {
    assertEquals(SyncError.HttpError(500), fetch(server(reply(500)).base()).error())
  }

  @Test
  fun timeout_isUnreachable() = runTest {
    val slow =
      MockResponse.Builder().code(200).body(okBody).bodyDelay(2, TimeUnit.SECONDS).build()
    assertEquals(SyncError.Unreachable, fetch(server(slow).base()).error())
  }

  @Test
  fun badJson_isBadResponse() = runTest {
    assertEquals(SyncError.BadResponse, fetch(server(reply(200, "{nope")).base()).error())
  }

  @Test
  fun htmlBody_isBadResponse() = runTest {
    assertEquals(
      SyncError.BadResponse,
      fetch(server(reply(200, "<html>login</html>")).base()).error(),
    )
  }

  @Test
  fun missingCharge_isBadResponse() = runTest {
    assertEquals(SyncError.BadResponse, fetch(server(reply(200, """{"soc":null}""")).base()).error())
  }

  @Test
  fun nonFiniteValues_areMissing() {
    val r =
      TemplateResponse(
        soc = 50.0,
        power = Double.NaN,
        current = Double.POSITIVE_INFINITY,
        totalV = Double.NEGATIVE_INFINITY,
        cells = listOf(3.3, Double.NaN),
      )
    val reading = r.toReading(1L)!!
    assertNull(reading.powerW)
    assertNull(reading.currentA)
    assertNull(reading.totalVoltageV)
    assertEquals(listOf(3.3, null), reading.cellVoltagesV)
    assertNull(TemplateResponse(soc = Double.NaN).toReading(1L))
    assertNull(TemplateResponse(soc = Double.POSITIVE_INFINITY).toReading(1L))
  }

  @Test
  fun fallback_usedAfterPrimaryFails() = runTest {
    val fb = server(reply(200, okBody))
    assertTrue(fetch(closedUrl(), fb.base()) is SyncResult.Success)
  }

  @Test
  fun bothFail_primaryAuthWinsOverUnreachable() = runTest {
    assertEquals(
      SyncError.Unauthorized,
      fetch(server(reply(401)).base(), closedUrl()).error(),
    )
  }

  @Test
  fun bothFail_unreachablePrimaryGivesFallbackError() = runTest {
    assertEquals(SyncError.HttpError(500), fetch(closedUrl(), server(reply(500)).base()).error())
  }

  @Test
  fun trailingSlash_requestsApiTemplate() = runTest {
    val s = server(reply(200, okBody))
    fetch(s.base() + "/")
    assertEquals("/api/template", s.takeRequest().url.encodedPath)
  }

  @Test
  fun sameFallback_requestsOnce() = runTest {
    val s = server(reply(500), reply(500))
    fetch(s.base(), s.base() + "/")
    assertEquals(1, s.requestCount)
  }

  @Test
  fun insecureUrl_noRequest() = runTest {
    assertEquals(SyncError.InsecureConnection, fetch("http://example.com").error())
  }

  @Test
  fun errorsNeverContainToken() = runTest {
    val errors =
      listOf(
        fetch(server(reply(401)).base()),
        fetch(server(reply(500)).base()),
        fetch(server(reply(200, "<html>")).base()),
        fetch(closedUrl()),
        fetch("http://example.com"),
        fetch("not a url"),
      )
    errors.forEach { assertFalse(it.error().toString().contains("t0ken")) }
  }

  @Test
  fun pickError_prefersSpecific() {
    assertEquals(SyncError.BadResponse, pickError(SyncError.BadResponse, SyncError.Unauthorized))
    assertEquals(SyncError.Unauthorized, pickError(SyncError.Unreachable, SyncError.Unauthorized))
  }
}
