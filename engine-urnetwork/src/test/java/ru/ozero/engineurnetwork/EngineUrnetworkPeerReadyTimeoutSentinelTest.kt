package ru.ozero.engineurnetwork

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertTrue

class EngineUrnetworkPeerReadyTimeoutSentinelTest {
    @Test
    fun `peer ready timeout is five minutes`() {
        val src = locateSource("engine-urnetwork/src/main/java/ru/ozero/engineurnetwork/EngineUrnetwork.kt")
        val text = src.readText(Charsets.UTF_8)
        assertTrue(
            text.contains("PEER_READY_TIMEOUT_MS = 300_000L"),
            "URnetwork must keep searching peers for 5 minutes before awaitReady timeout",
        )
    }

    private fun locateSource(rel: String): File {
        var dir = File(System.getProperty("user.dir") ?: ".").absoluteFile
        repeat(5) {
            val candidate = File(dir, rel)
            if (candidate.isFile) return candidate
            dir = dir.parentFile ?: return@repeat
        }
        error("$rel not found from ${System.getProperty("user.dir")}")
    }
}
