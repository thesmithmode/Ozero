package ru.ozero.commonvpn

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertTrue

class StartSequenceCoordinatorAutoRetryContractTest {

    private val source by lazy {
        val moduleRoot = File(System.getProperty("user.dir") ?: ".")
        val f = File(moduleRoot, "src/main/java/ru/ozero/commonvpn/StartSequenceCoordinator.kt")
        assertTrue(f.exists(), "StartSequenceCoordinator.kt not found: $f")
        f.readText()
    }

    @Test
    fun `auto candidate reset emits Disconnecting only from active start states`() {
        val body = source.substringAfter("private suspend fun resetAfterAutoCandidateFailure(")
            .substringBefore("companion object")
        assertTrue(
            body.contains("deps.tunnelController.state.value"),
            "resetAfterAutoCandidateFailure must inspect current UI state before emitting Disconnecting.",
        )
        assertTrue(
            body.contains("state is TunnelState.Connected") &&
                body.contains("state is TunnelState.Connecting") &&
                body.contains("state is TunnelState.Probing"),
            "auto retry cleanup may emit Disconnecting only from active start/connect states.",
        )
        assertTrue(
            body.contains("deps.tunnelController.reset()"),
            "auto retry cleanup must reset transient state before next candidate.",
        )
    }

    @Test
    fun `intermediate auto candidate failures stay non terminal`() {
        val runBody = source.substringAfter("suspend fun run(").substringBefore("private suspend fun startSingleEngineCandidate(")
        assertTrue(
            runBody.contains("notifyFailure = manualEngine != null || isLast"),
            "Only manual mode or the last auto candidate may notify terminal engine failure.",
        )
    }
}
