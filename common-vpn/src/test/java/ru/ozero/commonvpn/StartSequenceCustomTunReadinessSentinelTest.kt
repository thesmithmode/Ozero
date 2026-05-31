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
    fun `custom TUN startup timeout does not fast-fail before peer watchdog`() {
        val body = source.substringAfter("private suspend fun startSingleEngineCandidate(")
            .substringBefore("private suspend fun runSingleProxy(")
        assertTrue(
            body.contains("awaitEngineReady(activeEngineId, allowStartupTimeout = usesCustomTun)"),
            "WARP/URnetwork attach TUN before first peer handshake. Startup timeout must not tear " +
                "down custom-TUN engines; peer watchdog owns delayed handshake recovery.",
        )

        val awaitBody = source.substringAfter("private suspend fun awaitEngineReady(")
            .substringBefore("private fun buildEngineConfig(")
        assertTrue(
            awaitBody.contains("if (allowStartupTimeout)") &&
                awaitBody.contains("peer watchdog owns recovery") &&
                awaitBody.contains("return true"),
            "awaitReady Timeout for custom-TUN engines must continue with explicit diagnostic log.",
        )
    }
}
