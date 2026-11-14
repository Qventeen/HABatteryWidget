package io.lumensync.habattery.domain

import io.lumensync.habattery.domain.UrlPolicy.Verdict.INSECURE
import io.lumensync.habattery.domain.UrlPolicy.Verdict.INVALID
import io.lumensync.habattery.domain.UrlPolicy.Verdict.OK
import org.junit.Assert.assertEquals
import org.junit.Test

class UrlPolicyTest {
  private fun v(u: String) = UrlPolicy.check(u)

  @Test
  fun ok() =
    listOf(
        "https://example.com",
        "http://192.168.1.10:8123",
        "http://10.0.2.2:8123",
        "http://172.16.0.1",
        "http://100.64.0.1",
        "http://homeassistant",
        "http://ha.local:8123",
        "http://localhost:8123",
        "http://127.0.0.1:5000",
        "http://[fd00::1]:8123",
        "http://[fe80::1]",
        "http://[::1]",
        "http://192.168.1.10:8123/",
      )
      .forEach { assertEquals(it, OK, v(it)) }

  @Test
  fun insecure() =
    listOf(
        "http://example.com",
        "http://172.32.0.1",
        "http://100.128.0.1",
        "http://[2001:db8::1]",
        "http://8.8.8.8",
      )
      .forEach { assertEquals(it, INSECURE, v(it)) }

  @Test
  fun invalid() =
    listOf("", "ftp://x", "not a url", "https://", "192.168.1.2").forEach {
      assertEquals(it, INVALID, v(it))
    }
}
