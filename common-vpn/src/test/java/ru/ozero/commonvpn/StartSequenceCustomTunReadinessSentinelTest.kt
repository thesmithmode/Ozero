package ru.ozero.commonvpn

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertTrue

class StartSequenceCustomTunReadinessSentinelTest {

    private val source by lazy {
        val moduleRoot = File(System.getProperty("user.dir") ?: ".")
        val f = File(moduleRoot, "src/main/java/ru/ozero/commonvpn/StartSequenceCoordinator.kt")
        assertTrue(f.exists(), "StartSequenceCoordinator.kt not found: $f")
        f.readText()
    }

    @Test
    fun `custom TUN startup timeout fast-fails before Connected`() {
        val body = source.substringAfter("private suspend fun startSingleEngineCandidate(")
            .substringBefore("private suspend fun runSingleProxy(")
        assertTrue(
            body.contains("if (!awaitEngineReady(activeEngineId))"),
            "TUN startup must not publish Connected until awaitReady proves a real engine handshake.",
        )

        val awaitBody = source.substringAfter("private suspend fun awaitEngineReady(")
            .substringBefore("private fun buildEngineConfig(")
        assertTrue(
            awaitBody.contains("ReadyResult.Timeout") &&
                awaitBody.contains("engineFailure (fast-fail)") &&
                awaitBody.contains("false"),
            "awaitReady Timeout must remain a startup failure; peer watchdog starts only after Connected.",
        )
        assertTrue(!source.contains("allowStartupTimeout"))
        assertTrue(!source.contains("allowsStartupTimeout"))
        assertTrue(!source.contains("peer watchdog owns recovery"))
    }
}
