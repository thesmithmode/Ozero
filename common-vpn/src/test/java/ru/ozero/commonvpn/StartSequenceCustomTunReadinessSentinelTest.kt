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
            body.contains(
                "awaitEngineReady(activeEngineId, allowStartupTimeout = allowsStartupTimeout(activeEngineId))",
            ),
            "Only engines whose peer-watchdog policy can recover before the first peer may continue after " +
                "startup timeout. FPTN/sing-box custom TUN startup timeout must stay fast-fail.",
        )

        val awaitBody = source.substringAfter("private suspend fun awaitEngineReady(")
            .substringBefore("private fun buildEngineConfig(")
        assertTrue(
            awaitBody.contains("if (allowStartupTimeout)") &&
                awaitBody.contains("peer watchdog owns recovery") &&
                awaitBody.contains("return true"),
            "awaitReady Timeout for custom-TUN engines must continue with explicit diagnostic log.",
        )

        val policyBody = source.substringAfter("private fun allowsStartupTimeout(")
            .substringBefore("private suspend fun establishTunAndChain(")
        assertTrue(
            policyBody.contains("peerWatchdogPolicy()") &&
                policyBody.contains("recoverBeforeFirstPeer == true"),
            "Startup timeout continuation must be policy-driven, not blanket TunFdAcceptor behavior.",
        )
    }
}
