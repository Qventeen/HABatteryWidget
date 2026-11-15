package io.lumensync.habattery.domain

import io.lumensync.habattery.domain.UrlPolicy.Verdict.PUBLIC_HTTP
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
  fun publicHttp() =
    listOf(
        "http://example.com",
        "http://172.32.0.1",
        "http://100.128.0.1",
        "http://[2001:db8::1]",
        "http://8.8.8.8",
      )
      .forEach { assertEquals(it, PUBLIC_HTTP, v(it)) }

  @Test
  fun invalid() =
    listOf("", "ftp://x", "not a url", "https://", "192.168.1.2").forEach {
      assertEquals(it, INVALID, v(it))
    }

  @Test
  fun rangeBoundaries() {
    listOf(
        "http://172.16.0.0",
        "http://172.31.255.255",
        "http://100.64.0.0",
        "http://100.127.0.1",
        "http://[fc00::1]",
        "http://[fdff::1]",
        "http://[febf::1]",
      )
      .forEach { assertEquals(it, OK, v(it)) }
    listOf(
        "http://172.15.0.1",
        "http://172.32.0.1",
        "http://100.63.0.1",
        "http://100.128.0.1",
        "http://[fbff::1]",
        "http://[fe7f::1]",
        "http://[fec0::1]",
      )
      .forEach { assertEquals(it, PUBLIC_HTTP, v(it)) }
  }

  @Test
  fun schemeAndHostIgnoreCase() {
    assertEquals(OK, v("HTTP://LOCALHOST"))
    assertEquals(OK, v("HTTP://HA.LOCAL:8123"))
  }

  @Test
  fun userInfo_usesRealHost() {
    assertEquals(OK, v("http://user@192.168.1.2"))
    assertEquals(PUBLIC_HTTP, v("http://192.168.1.1@evil.com"))
  }

  @Test
  fun trailingDot_isPublicHttp() = assertEquals(PUBLIC_HTTP, v("http://ha.local."))

  @Test
  fun numericSingleLabel_isPublicHttp() =
    listOf("http://134744072", "http://3232235777", "http://0", "http://00:8123").forEach {
      assertEquals(it, PUBLIC_HTTP, v(it))
    }

  @Test
  fun hexLabel_isPublicHttp() =
    listOf("http://0x08080808", "http://0XC0A80101", "http://0x").forEach {
      assertEquals(it, PUBLIC_HTTP, v(it))
    }

  @Test
  fun leadingZeroOctet_isPublicHttp() =
    listOf(
        "http://010.0.0.1",
        "http://010.001.001.005",
        "http://0177.0.0.1",
        "http://192.168.1.010",
        "http://192.168.01.1",
      )
      .forEach { assertEquals(it, PUBLIC_HTTP, v(it)) }

  @Test
  fun zeroOctet_isNotLeadingZero() = assertEquals(OK, v("http://10.0.0.1"))

  @Test
  fun hexInDottedHost_isRejectedByUriParser() = assertEquals(INVALID, v("http://0x8.0.0.1"))

  @Test fun `public http is allowed with a warning`() = assertEquals(PUBLIC_HTTP, v("http://example.com"))

  @Test fun `messy input classifies like the clean form`() = assertEquals(PUBLIC_HTTP, v(" HTTP://Example.com/ "))

  @Test fun `other schemes`() = assertEquals(UrlPolicy.Verdict.INVALID, v("ftp://example.com"))
}
