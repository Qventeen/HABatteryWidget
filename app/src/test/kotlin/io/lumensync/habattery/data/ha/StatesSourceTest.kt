package io.lumensync.habattery.data.ha

import io.lumensync.habattery.domain.model.BatteryReading
import io.lumensync.habattery.domain.model.ConnectionSettings
import io.lumensync.habattery.domain.model.EntitySettings
import io.lumensync.habattery.domain.model.SensorList
import io.lumensync.habattery.domain.model.SyncError
import io.lumensync.habattery.domain.model.SyncResult
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.test.runTest
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StatesSourceTest {
  private val servers = mutableListOf<MockWebServer>()
  // The app's client with short timeouts, so the tests keep its other rules (no redirects).
  private val client =
    defaultHttpClient()
      .newBuilder()
      .connectTimeout(300, TimeUnit.MILLISECONDS)
      .readTimeout(1, TimeUnit.SECONDS)
      .callTimeout(2, TimeUnit.SECONDS)
      .build()
  private val source = StatesSource(client) { 1234L }

  /** 18 different ids: every single field and 8 temperatures. */
  private val all =
    EntitySettings(
      charge = "sensor.soc",
      totalVoltage = "sensor.volts",
      current = "sensor.amps",
      power = "sensor.watts",
      capacityRemaining = "sensor.ah_left",
      totalCapacity = "sensor.ah_total",
      averageCellVoltage = "sensor.cell_avg",
      deltaCellVoltage = "sensor.cell_delta",
      mosfetTemperature = "sensor.mosfet",
      temperatures = List(8) { "sensor.t${it + 1}" },
      balancing = "binary_sensor.balancing",
    )

  private val allStates =
    mapOf(
      "sensor.soc" to state("55"),
      "sensor.volts" to state("26.5"),
      "sensor.amps" to state("-10"),
      "sensor.watts" to state("-500"),
      "sensor.ah_left" to state("154"),
      "sensor.ah_total" to state("280"),
      "sensor.cell_avg" to state(" 3.312 "),
      "sensor.cell_delta" to state("0.005"),
      "sensor.mosfet" to state("31.5"),
      "binary_sensor.balancing" to state("on"),
    ) + (1..8).associate { "sensor.t$it" to state("2$it") }

  /** Answers `/api/states/<id>` from [answers]; an id it does not know gets 404, as in HA. */
  private class StatesDispatcher(
    private val answers: Map<String, MockResponse>,
    private val delayMillis: Long = 0,
  ) : Dispatcher() {
    private val open = AtomicInteger()
    val maxOpen = AtomicInteger()

    override fun dispatch(request: RecordedRequest): MockResponse {
      maxOpen.accumulateAndGet(open.incrementAndGet(), Math::max)
      try {
        Thread.sleep(delayMillis)
        return answers[request.url.encodedPath.removePrefix("/api/states/")] ?: reply(404)
      } finally {
        open.decrementAndGet()
      }
    }
  }

  @After fun tearDown() = servers.forEach { runCatching { it.close() } }

  private fun server(dispatcher: Dispatcher): MockWebServer {
    val s = MockWebServer()
    s.dispatcher = dispatcher
    s.start()
    servers += s
    return s
  }

  private fun server(answers: Map<String, MockResponse>): MockWebServer =
    server(StatesDispatcher(answers))

  private fun MockWebServer.base(): String =
    url("/").newBuilder().host("127.0.0.1").build().toString()

  private suspend fun fetch(
    url: String,
    entities: EntitySettings = all,
    token: String = "t0ken",
  ): SyncResult = source.fetch(ConnectionSettings(url), entities, token)

  private fun SyncResult.error(): SyncError = (this as SyncResult.Failure).error

  private fun SyncResult.reading(): BatteryReading = (this as SyncResult.Success).reading

  private fun MockWebServer.paths(): List<String> =
    List(requestCount) { takeRequest().url.encodedPath }

  @Test
  fun allValues_sameReadingAsTemplate() = runTest {
    val expected =
      TemplateResponse(
          soc = 55.0,
          totalV = 26.5,
          current = -10.0,
          power = -500.0,
          ahLeft = 154.0,
          capacity = 280.0,
          avgCell = 3.312,
          delta = 0.005,
          mosfet = 31.5,
          temps = listOf(21.0, 22.0, 23.0, 24.0, 25.0, 26.0, 27.0, 28.0),
          balancing = "on",
        )
        .toReading(1234L)
    assertEquals(SyncResult.Success(expected!!), fetch(server(allStates).base()))
  }

  @Test
  fun blankFields_makeNoRequest() = runTest {
    val s = server(allStates)
    val result = fetch(s.base(), EntitySettings(charge = "sensor.soc"))
    assertEquals(55.0, result.reading().chargePercent, 0.0)
    assertEquals(1, s.requestCount)
    val request = s.takeRequest()
    assertEquals("GET", request.method)
    assertEquals("/api/states/sensor.soc", request.url.encodedPath)
  }

  @Test
  fun duplicateId_oneRequest_bothFilled() = runTest {
    val s = server(allStates)
    val entities =
      EntitySettings(
        charge = "sensor.soc",
        mosfetTemperature = "sensor.t1",
        temperatures = listOf("sensor.t1"),
      )
    val reading = fetch(s.base(), entities).reading()
    assertEquals(21.0, reading.mosfetTemperatureC!!, 0.0)
    assertEquals(listOf(21.0), reading.temperaturesC)
    assertEquals(1, s.paths().count { it == "/api/states/sensor.t1" })
  }

  @Test
  fun missingEntity404_isNoValue() = runTest {
    val reading = fetch(server(allStates - "sensor.watts" - "sensor.t2").base()).reading()
    assertNull(reading.powerW)
    assertEquals(listOf(21.0, null, 23.0, 24.0, 25.0, 26.0, 27.0, 28.0), reading.temperaturesC)
  }

  @Test
  fun unavailable_unknown_text_areNoValue() = runTest {
    val states =
      allStates +
        mapOf(
          "sensor.volts" to state("unavailable"),
          "sensor.amps" to state("unknown"),
          "sensor.watts" to state("abc"),
          "sensor.ah_left" to state(""),
          "binary_sensor.balancing" to state("unavailable"),
        )
    val reading = fetch(server(states).base()).reading()
    assertNull(reading.totalVoltageV)
    assertNull(reading.currentA)
    assertNull(reading.powerW)
    assertNull(reading.capacityRemainingAh)
    assertNull(reading.balancing)
  }

  @Test
  fun noStateField_isNoValue() = runTest {
    val states =
      allStates +
        mapOf(
          "sensor.volts" to reply(200, """{"entity_id":"sensor.volts"}"""),
          "sensor.amps" to reply(200, """{"entity_id":"sensor.amps","state":null}"""),
        )
    val reading = fetch(server(states).base()).reading()
    assertNull(reading.totalVoltageV)
    assertNull(reading.currentA)
  }

  @Test
  fun missingCharge_isBadResponse() = runTest {
    assertEquals(SyncError.BadResponse, fetch(server(allStates - "sensor.soc").base()).error())
  }

  @Test
  fun htmlBody_isBadResponse() = runTest {
    val states = allStates + ("sensor.watts" to reply(200, "<html>login</html>"))
    assertEquals(SyncError.BadResponse, fetch(server(states).base()).error())
  }

  @Test
  fun oneUnauthorized_isUnauthorized() = runTest {
    val states = allStates + ("sensor.t3" to reply(401))
    assertEquals(SyncError.Unauthorized, fetch(server(states).base()).error())
  }

  @Test
  fun status503_isHttpError503() = runTest {
    val states = allStates + ("sensor.amps" to reply(503))
    assertEquals(SyncError.HttpError(503), fetch(server(states).base()).error())
  }

  @Test
  fun closedPort_isUnreachable() = runTest {
    val s = server(allStates)
    val url = s.base()
    s.close()
    assertEquals(SyncError.Unreachable, fetch(url).error())
  }

  @Test
  fun failure_stopsTheRequestsNotSentYet() = runTest {
    // The others answer late, so the 401 arrives while most ids still wait for a slot.
    val states = allStates.mapValues { (_, body) -> body.slow() } + ("sensor.soc" to reply(401))
    val s = server(states)
    assertEquals(SyncError.Unauthorized, fetch(s.base()).error())
    assertTrue("requests: ${s.requestCount}", s.requestCount < 18)
  }

  @Test
  fun failure_cancelsRunningRequests() = runTest {
    // Timeouts longer than the held answer, so only cancelling the call can end it early.
    val patientClient =
      client
        .newBuilder()
        .readTimeout(10, TimeUnit.SECONDS)
        .callTimeout(10, TimeUnit.SECONDS)
        .build()
    val patient = StatesSource(patientClient) { 1234L }
    val held = state("-500").newBuilder().headersDelay(5, TimeUnit.SECONDS).build()
    val heldArrived = CountDownLatch(1)
    // The 401 waits until the held request has reached the server, so that request is running.
    val dispatcher =
      object : Dispatcher() {
        override fun dispatch(request: RecordedRequest): MockResponse =
          if (request.url.encodedPath == "/api/states/sensor.watts") {
            heldArrived.countDown()
            held
          } else {
            heldArrived.await(2, TimeUnit.SECONDS)
            reply(401)
          }
      }
    val s = server(dispatcher)
    val entities = EntitySettings(charge = "sensor.soc", power = "sensor.watts")
    val started = System.nanoTime()
    val result = patient.fetch(ConnectionSettings(s.base()), entities, "t0ken")
    val millis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)
    assertEquals(SyncError.Unauthorized, result.error())
    assertEquals(2, s.requestCount)
    assertTrue("took $millis ms", millis < 2_000)
  }

  @Test
  fun atMostFourOpen() = runTest {
    val dispatcher = StatesDispatcher(allStates, delayMillis = 100)
    val s = server(dispatcher)
    assertTrue(fetch(s.base()) is SyncResult.Success)
    assertEquals(18, s.requestCount)
    assertTrue("max open: ${dispatcher.maxOpen}", dispatcher.maxOpen.get() in 2..4)
  }

  @Test
  fun invalidId_noRequest() = runTest {
    val s = server(allStates)
    val result = fetch(s.base(), EntitySettings(charge = "sensor.a/../b"))
    assertEquals(SyncError.BadResponse, result.error())
    assertEquals(0, s.requestCount)
  }

  @Test
  fun tokenOnlyInHeader() = runTest {
    val s = server(allStates)
    fetch(s.base())
    assertEquals(18, s.requestCount)
    repeat(s.requestCount) {
      val request = s.takeRequest()
      assertEquals("Bearer t0ken", request.headers["Authorization"])
      assertFalse(request.url.toString().contains("t0ken"))
    }
  }

  private suspend fun search(url: String, prefix: String = "bms"): SensorList =
    source.listTemperatureSensors(ConnectionSettings(url), prefix, "t0ken")

  private fun list(vararg ids: String, bodyOf: (String) -> String = { """{"entity_id":"$it"}""" }) =
    ids.joinToString(",", "[", "]", transform = bodyOf)

  @Test
  fun sensorSearch_listsMatchingIds() = runTest {
    val body =
      list(
        "sensor.bms_temperature_sensor_2",
        "sensor.bms_temperature_sensor_1",
        "sensor.other",
        "sensor.otherbms_temperature_sensor_3",
      )
    val s = server(mapOf("/api/states" to reply(200, body)))
    val result = search(s.base())
    assertEquals(
      SensorList.Found(listOf("sensor.bms_temperature_sensor_1", "sensor.bms_temperature_sensor_2")),
      result,
    )
    assertEquals(1, s.requestCount)
    val request = s.takeRequest()
    assertEquals("GET", request.method)
    assertEquals("/api/states", request.url.encodedPath)
    assertEquals("Bearer t0ken", request.headers["Authorization"])
  }

  @Test
  fun sensorSearch_largeList() = runTest {
    val ids =
      (1..1995).map { "sensor.noise_$it" } + (5 downTo 1).map { "sensor.bms_temperature_sensor_$it" }
    val s = server(mapOf("/api/states" to reply(200, list(*ids.toTypedArray()))))
    assertEquals(
      SensorList.Found((1..5).map { "sensor.bms_temperature_sensor_$it" }),
      search(s.base()),
    )
  }

  @Test
  fun sensorSearch_itemWithoutEntityId_isSkipped() = runTest {
    val body = """[{"state":"1"},{"entity_id":"sensor.bms_temperature_sensor_1"}]"""
    val s = server(mapOf("/api/states" to reply(200, body)))
    assertEquals(SensorList.Found(listOf("sensor.bms_temperature_sensor_1")), search(s.base()))
  }

  @Test
  fun sensorSearch_badJson_isBadResponse() = runTest {
    val s = server(mapOf("/api/states" to reply(200, "not json")))
    assertEquals(SensorList.Failed(SyncError.BadResponse), search(s.base()))
  }

  @Test
  fun sensorSearch_invalidPrefix_noRequest() = runTest {
    val s = server(mapOf("/api/states" to reply(200, "[]")))
    assertEquals(SensorList.Found(emptyList<String>()), search(s.base(), "bad prefix!"))
    assertEquals(0, s.requestCount)
  }

  @Test
  fun sensorSearch_httpError_isFailed() = runTest {
    val s = server(mapOf("/api/states" to reply(401)))
    assertEquals(SensorList.Failed(SyncError.Unauthorized), search(s.base()))
  }

  private companion object {
    fun reply(code: Int, body: String = ""): MockResponse =
      MockResponse.Builder().code(code).body(body).build()

    fun state(value: String): MockResponse =
      reply(200, """{"entity_id":"sensor.x","state":"$value","attributes":{}}""")

    fun MockResponse.slow(): MockResponse =
      newBuilder().headersDelay(100, TimeUnit.MILLISECONDS).build()
  }
}
