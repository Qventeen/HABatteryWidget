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
    val labels = host.split('.')
    // The system resolver (inet_aton) reads "134744072", "0x08080808" or "010.0.0.1" as some
    // IPv4 address. Only the plain dotted-decimal form is trusted; every other numeric form is not.
    if (labels.all { NUMERIC_LABEL.matches(it) }) {
      val octets = labels.takeIf { it.size == 4 && it.all { l -> OCTET.matches(l) } }
      return octets != null && isLocalIpv4(octets.map(String::toInt))
    }
    return host == "localhost" || '.' !in host || host.endsWith(".local")
  }

  /** A decimal, octal or hex number, as inet_aton reads it. */
  private val NUMERIC_LABEL = Regex("[0-9]+|0x[0-9a-f]*")

  /** A plain decimal octet: 0–255, no leading zero. */
  private val OCTET = Regex("0|[1-9][0-9]?|1[0-9]{2}|2[0-4][0-9]|25[0-5]")

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
