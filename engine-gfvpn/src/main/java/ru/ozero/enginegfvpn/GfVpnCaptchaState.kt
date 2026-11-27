package ru.ozero.enginegfvpn

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.net.URI

/** In-memory manual challenge. Never persist or log the capability URL. */
object GfVpnCaptchaState {
    class Challenge internal constructor(
        internal val owner: Any,
        val url: String,
        private val origin: URI,
        internal val dismiss: Runnable,
    ) {
        fun permits(candidate: String): Boolean {
            val parsed = localUri(candidate) ?: return false
            return parsed.scheme == origin.scheme &&
                parsed.host.equals(origin.host, ignoreCase = true) &&
                parsed.port == origin.port
        }

        override fun toString(): String = "gfVPN manual verification (URL redacted)"
    }

    private val current = MutableStateFlow<Challenge?>(null)
    val challenge = current.asStateFlow()

    @JvmStatic
    @Synchronized
    fun publish(owner: Any, url: String, dismiss: Runnable): Boolean {
        val origin = localUri(url) ?: return false
        val previous = current.value
        if (previous?.owner === owner && previous.url == url) return true
        current.value = Challenge(owner, url, origin, dismiss)
        return true
    }

    @JvmStatic
    @Synchronized
    fun clear(owner: Any): Boolean {
        if (current.value?.owner !== owner) return false
        current.value = null
        return true
    }

    internal suspend fun awaitResolution(owner: Any, timeoutMs: Long = 180_000L): Boolean =
        withTimeoutOrNull(timeoutMs) {
            challenge.first { it?.owner !== owner }
            true
        } == true

    fun cancel(challenge: Challenge) {
        val dismiss = synchronized(this) {
            if (current.value !== challenge) return
            current.value = null
            challenge.dismiss
        }
        dismiss.run()
    }

    private fun localUri(raw: String): URI? {
        if (raw.length !in 1..8192) return null
        val uri = runCatching { URI(raw) }.getOrNull() ?: return null
        val loopback = uri.host?.lowercase() in setOf("127.0.0.1", "localhost")
        val validOrigin = uri.scheme in setOf("http", "https") && uri.port in 1..65535
        return uri.takeIf { loopback && validOrigin && uri.rawUserInfo == null }
    }
}
