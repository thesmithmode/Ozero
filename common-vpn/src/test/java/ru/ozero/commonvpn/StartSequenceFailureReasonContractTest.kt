package ru.ozero.commonvpn

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertTrue

class StartSequenceFailureReasonContractTest {

    @Test
    fun `startChain reports ChainResult failure reason instead of data class toString`() {
        val source = File(
            System.getProperty("user.dir") ?: ".",
            "src/main/java/ru/ozero/commonvpn/StartSequenceCoordinator.kt",
        ).readText()
        val body = source.substringAfter("private suspend fun startChain(")
            .substringBefore("private suspend fun routeTrafficForEngine(")

        assertTrue(
            body.contains("is ChainResult.Failure -> chainResult.reason"),
            "startChain must pass the original ChainOrchestrator failure reason.",
        )
        assertTrue(
            !body.contains("chainResult?.toString()"),
            "startChain must not expose raw ChainResult.Failure.toString() as a user-visible reason.",
        )
    }
}
