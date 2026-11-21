package io.lumensync.habattery.data.ha

import io.lumensync.habattery.data.store.StoreJson
import io.lumensync.habattery.domain.model.BatteryReading
import io.lumensync.habattery.domain.model.ConnectionSettings
import io.lumensync.habattery.domain.model.EntitySettings
import io.lumensync.habattery.domain.model.FetchRequest
import io.lumensync.habattery.domain.model.FetchResult
import io.lumensync.habattery.domain.model.InverterEntitySettings
import io.lumensync.habattery.domain.model.InverterReading
import io.lumensync.habattery.domain.model.PartResult
import io.lumensync.habattery.domain.model.SensorList
import io.lumensync.habattery.domain.model.SyncError
import java.net.InetAddress
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
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
  private val batteryOnly = FetchRequest(entities, null)
  private val inverter =
    InverterEntitySettings(
      mode = "sensor.inv_operation_mode",
      gridVoltage = "sensor.inv_main_voltage",
      outputVoltage = "sensor.inv_output_voltage",
    )
  private val both = FetchRequest(entities, inverter)
  private val okBody =
    """{"soc":55.0,"power":-500.0,"current":-10.0,"total_v":26.5}"""

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

  private suspend fun fetch(
    url: String,
    token: String = "t0ken",
    request: FetchRequest = batteryOnly,
  ): FetchResult = source.fetch(ConnectionSettings(url), request, token)

  private fun FetchResult.error(): SyncError = (battery as PartResult.Failed).error

  private fun failedBoth(error: SyncError) =
    FetchResult(PartResult.Failed(error), PartResult.Failed(error))

  private fun RecordedRequest.template(): String =
    StoreJson.parseToJsonElement(body!!.utf8()).jsonObject["template"]!!.jsonPrimitive.content

  @Test
  fun success_parsesAndSendsRequest() = runTest {
    val s = server(reply(200, okBody))
    val result = fetch(s.base())
    assertEquals(
      FetchResult(
        PartResult.Ok(BatteryReading(55.0, -500.0, -10.0, 26.5, updatedAtMillis = 1234L)),
        null,
      ),
      result,
    )
    val req = s.takeRequest()
    assertEquals("POST", req.method)
    assertEquals("/api/template", req.url.encodedPath)
    assertEquals("Bearer t0ken", req.headers["Authorization"])
    assertTrue(req.body!!.utf8().contains("\"template\""))
  }

  @Test
  fun `public http is sent`() = runTest {
    // example.com resolves to the local mock, so the request goes out over plain HTTP.
    val publicSource =
      HomeAssistantSource(
        client.newBuilder().dns { listOf(InetAddress.getLoopbackAddress()) }.build()
      ) {
        1234L
      }
    val s = server(reply(200, okBody))
    val result =
      publicSource.fetch(ConnectionSettings("http://example.com:${s.port}"), batteryOnly, "t0ken")
    assertTrue(result.battery is PartResult.Ok)
    assertEquals(1, s.requestCount)
    assertEquals("Bearer t0ken", s.takeRequest().headers["Authorization"])
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
  fun batteryFails_inverterOk() = runTest {
    val body = """{"soc":null,"inv_mode":"Mains","inv_grid_v":231.0,"inv_out_v":230.0}"""
    val result = fetch(server(reply(200, body)).base(), request = both)
    assertEquals(PartResult.Failed(SyncError.BadResponse), result.battery)
    assertEquals(PartResult.Ok(InverterReading("Mains", 231.0, 230.0, 1234L)), result.inverter)
  }

  @Test
  fun inverterAllUnavailable_isBadResponse() = runTest {
    val body = """{"soc":55.0,"inv_mode":"unavailable","inv_grid_v":null,"inv_out_v":null}"""
    val result = fetch(server(reply(200, body)).base(), request = both)
    assertEquals(PartResult.Ok(BatteryReading(55.0, updatedAtMillis = 1234L)), result.battery)
    assertEquals(PartResult.Failed(SyncError.BadResponse), result.inverter)
  }

  @Test
  fun unauthorized_failsBoth() = runTest {
    val result = fetch(server(reply(401)).base(), request = both)
    assertEquals(failedBoth(SyncError.Unauthorized), result)
  }

  @Test
  fun otherWholeRequestFailures_failBoth() = runTest {
    suspend fun failure(url: String) = fetch(url, request = both)
    assertEquals(failedBoth(SyncError.HttpError(500)), failure(server(reply(500)).base()))
    assertEquals(failedBoth(SyncError.BadResponse), failure(server(reply(200, "{nope")).base()))
    assertEquals(failedBoth(SyncError.Unreachable), failure(closedUrl()))
  }

  @Test
  fun onlyInverterAsked_batteryNull() = runTest {
    val body = """{"soc":null,"inv_mode":"Off-Grid","inv_grid_v":0.0,"inv_out_v":230.0}"""
    val s = server(reply(200, body))
    val result = fetch(s.base(), request = FetchRequest(null, inverter))
    val ok = PartResult.Ok(InverterReading("Off-Grid", 0.0, 230.0, 1234L))
    assertEquals(FetchResult(null, ok), result)
    val template = s.takeRequest().template()
    assertTrue(template, "\"soc\": none" in template)
    assertTrue(template, "\"inv_mode\": states('sensor.inv_operation_mode')" in template)
  }

  @Test
  fun nonFiniteValues_areMissing() {
    val r =
      TemplateResponse(
        soc = 50.0,
        power = Double.NaN,
        current = Double.POSITIVE_INFINITY,
        totalV = Double.NEGATIVE_INFINITY,
      )
    val reading = r.toReading(1L)!!
    assertNull(reading.powerW)
    assertNull(reading.currentA)
    assertNull(reading.totalVoltageV)
    assertNull(TemplateResponse(soc = Double.NaN).toReading(1L))
    assertNull(TemplateResponse(soc = Double.POSITIVE_INFINITY).toReading(1L))
  }

  @Test
  fun `posts once to the one url`() = runTest {
    val s = server(reply(500), reply(200, okBody))
    assertEquals(SyncError.HttpError(500), fetch(s.base()).error())
    assertEquals(1, s.requestCount)
  }

  @Test
  fun unreachable_isFailureWithoutFallback() = runTest {
    assertEquals(SyncError.Unreachable, fetch(closedUrl()).error())
  }

  @Test
  fun trailingSlash_requestsApiTemplate() = runTest {
    val s = server(reply(200, okBody))
    fetch(s.base() + "/")
    assertEquals("/api/template", s.takeRequest().url.encodedPath)
  }

  @Test
  fun errorsNeverContainToken() = runTest {
    val errors =
      listOf(
        fetch(server(reply(401)).base()),
        fetch(server(reply(500)).base()),
        fetch(server(reply(200, "<html>")).base()),
        fetch(closedUrl()),
        fetch("not a url"),
      )
    errors.forEach { assertFalse(it.error().toString().contains("t0ken")) }
  }

  private suspend fun sensors(url: String, prefix: String = "bms"): SensorList =
    source.listTemperatureSensors(ConnectionSettings(url), prefix, "t0ken")

  @Test
  fun `sensor list posts the template and parses the ids`() = runTest {
    val s = server(reply(200, """["sensor.bms_temperature_sensor_2","sensor.bms_temperature_sensor_1"]"""))
    assertEquals(
      SensorList.Found(listOf("sensor.bms_temperature_sensor_1", "sensor.bms_temperature_sensor_2")),
      sensors(s.base()),
    )
    val req = s.takeRequest()
    assertEquals("/api/template", req.url.encodedPath)
    assertEquals("Bearer t0ken", req.headers["Authorization"])
    assertTrue(req.body!!.utf8().contains("sensor[.]bms_temperature_sensor_[0-9]+$"))
  }

  @Test
  fun `sensor list with an invalid prefix makes no request`() = runTest {
    val s = server(reply(200, "[]"))
    listOf("", "Bad Prefix", "a'b").forEach { assertEquals(SensorList.Found(emptyList()), sensors(s.base(), it)) }
    assertEquals(0, s.requestCount)
  }

  @Test
  fun `sensor list trims the prefix like Fill`() = runTest {
    val s = server(reply(200, "[]"))
    sensors(s.base(), " bms_ ")
    assertTrue(s.takeRequest().body!!.utf8().contains("sensor[.]bms_temperature_sensor_"))
  }

  @Test
  fun `sensor list errors map like the sync`() = runTest {
    fun failed(r: SensorList) = (r as SensorList.Failed).error
    assertEquals(SyncError.Unauthorized, failed(sensors(server(reply(401)).base())))
    assertEquals(SyncError.HttpError(500), failed(sensors(server(reply(500)).base())))
    assertEquals(SyncError.BadResponse, failed(sensors(server(reply(200, "<html>")).base())))
    assertEquals(SyncError.Unreachable, failed(sensors(closedUrl())))
    assertEquals(SyncError.Unreachable, failed(sensors("not a url")))
  }
}
