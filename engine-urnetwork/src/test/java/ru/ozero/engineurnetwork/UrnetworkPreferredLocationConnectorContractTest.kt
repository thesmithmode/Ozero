package ru.ozero.engineurnetwork

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertTrue

class UrnetworkPreferredLocationConnectorContractTest {

    @Test
    fun `preferred location connector does not persist match eagerly`() {
        val source = File(
            System.getProperty("user.dir") ?: ".",
            "src/main/java/ru/ozero/engineurnetwork/UrnetworkPreferredLocationConnector.kt",
        ).readText()
        val matchBlock = source
            .substringAfter("if (attached.compareAndSet(false, true)) {")
            .substringBefore("Log.i(TAG, \"preferred ")

        assertTrue(
            matchBlock.contains("runCatching { cv.connect(match) }"),
            "match path must still call cv.connect(match) before the SDK selectedLocation listener persists state.",
        )
        assertTrue(
            !matchBlock.contains("persistLocation(device, match)"),
            "preferred location connector must not persist the match eagerly; SDK selectedLocation callback is the source of truth.",
        )
        assertTrue(
            matchBlock.indexOf("cv.connect(match)") >= 0,
            "match path must still invoke cv.connect(match).",
        )
        assertTrue(
            matchBlock.contains(".onFailure { PersistentLoggers.warn(TAG, \"connect(match) threw:"),
            "failed connect must not persist the location and should only log the error.",
        )
    }

    @Test
    fun `preferred location callbacks marshal SDK mutations onto main`() {
        val source = File(
            System.getProperty("user.dir") ?: ".",
            "src/main/java/ru/ozero/engineurnetwork/UrnetworkPreferredLocationConnector.kt",
        ).readText()

        assertTrue(
            source.contains("bridgeScope.launch(Dispatchers.Main.immediate)"),
            "preferred-location callbacks must be marshaled onto the main thread.",
        )
        assertTrue(
            source.contains("timeoutJob = bridgeScope.launch(Dispatchers.Main.immediate)"),
            "timeout fallback must also run on the main thread.",
        )
        assertTrue(
            !source.contains("timeoutJob.invokeOnCompletion { resolving.set(false) }"),
            "resolving must stay locked until the connect/cleanup path finishes, not until timeoutJob completion.",
        )
        assertTrue(
            source.contains("finish()"),
            "successful match and fallback cleanup must release resolving after cleanup finishes.",
        )
    }
}
