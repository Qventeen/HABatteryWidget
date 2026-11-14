package io.lumensync.habattery.domain

import java.net.URI
import java.net.URISyntaxException

/** Decides whether a Home Assistant base URL may be used. Cleartext HTTP is allowed only locally. */
object UrlPolicy {
  enum class Verdict {
    OK,
    INVALID,
    INSECURE,
  }

  fun check(url: String): Verdict {
    val uri =
      try {
        URI(url.trim())
      } catch (_: URISyntaxException) {
        return Verdict.INVALID
      }
    val host = uri.host?.removeSurrounding("[", "]")?.lowercase()
    if (host.isNullOrEmpty()) return Verdict.INVALID
    return when (uri.scheme?.lowercase()) {
      "https" -> Verdict.OK
      "http" -> if (isLocalHost(host)) Verdict.OK else Verdict.INSECURE
      else -> Verdict.INVALID
    }
  }

  private fun isLocalHost(host: String): Boolean {
    if (':' in host) return isLocalIpv6(host)
    parseIpv4(host)?.let { return isLocalIpv4(it) }
    return host == "localhost" || '.' !in host || host.endsWith(".local")
  }

  private fun parseIpv4(host: String): List<Int>? {
    val parts = host.split('.')
    if (parts.size != 4) return null
    val octets = parts.map { p -> p.takeIf { it.isNotEmpty() && it.all(Char::isDigit) }?.toIntOrNull() }
    return if (octets.all { it != null && it in 0..255 }) octets.filterNotNull() else null
  }

  private fun isLocalIpv4(o: List<Int>): Boolean =
    when (o[0]) {
      127,
      10 -> true
      172 -> o[1] in 16..31
      192 -> o[1] == 168
      100 -> o[1] in 64..127
      else -> false
    }

  private fun isLocalIpv6(host: String): Boolean {
    if (host == "::1") return true
    val first = host.substringBefore(':').toIntOrNull(16) ?: return false
    return (first and 0xFE00) == 0xFC00 || (first and 0xFFC0) == 0xFE80
  }
}
