package com.messengerz.core

import java.net.URI
import java.net.URLDecoder

/**
 * Removes tracking from links without changing where they go.
 *
 * - Unwraps Facebook's redirect shim (l.facebook.com/l.php?u=<real url>&h=...).
 * - Drops only known tracking parameters; every other parameter, its order, its encoding and
 *   the #fragment are kept byte-for-byte. Anything that doesn't parse cleanly is returned as-is.
 */
object LinkCleaner {
    private val SHIM_HOSTS = setOf("l.facebook.com", "lm.facebook.com", "l.messenger.com", "l.instagram.com")
    private val TRACKING = setOf(
        "fbclid", "igshid", "igsh", "mibextid", "__tn__", "__cft__", "__xts__",
        "gclid", "dclid", "msclkid", "twclid", "mc_eid", "yclid", "_hsenc", "_hsmi",
    )

    private fun isTracking(name: String) = name.lowercase().let { it in TRACKING || it.startsWith("utm_") }

    fun clean(url: String): String {
        if (!url.startsWith("http://", true) && !url.startsWith("https://", true)) return url
        return try {
            stripParams(unwrapShim(url))
        } catch (e: Exception) {
            url
        }
    }

    private fun unwrapShim(url: String): String {
        val uri = URI(url)
        val host = uri.host?.lowercase() ?: return url
        if (host !in SHIM_HOSTS || uri.path != "/l.php") return url
        val target = uri.rawQuery?.split('&')
            ?.firstOrNull { it.startsWith("u=") }
            ?.substring(2)
            ?.let { URLDecoder.decode(it, "UTF-8") }
            ?: return url
        // Only follow the shim to a real web link.
        return if (target.startsWith("http://", true) || target.startsWith("https://", true)) target else url
    }

    private fun stripParams(url: String): String {
        val hashAt = url.indexOf('#')
        val beforeHash = if (hashAt >= 0) url.substring(0, hashAt) else url
        val fragment = if (hashAt >= 0) url.substring(hashAt) else ""
        val q = beforeHash.indexOf('?')
        if (q < 0) return url
        val base = beforeHash.substring(0, q)
        val kept = beforeHash.substring(q + 1).split('&').filter { part ->
            part.isNotEmpty() && !isTracking(part.substringBefore('='))
        }
        return base + (if (kept.isEmpty()) "" else "?" + kept.joinToString("&")) + fragment
    }
}
