package ru.ozero.app.ui

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertTrue

class MainViewModelCancellationRethrowTest {

    private val mainSource by lazy {
        source("src/main/java/ru/ozero/app/ui/MainViewModel.kt")
    }

    private val resolverSource by lazy {
        source("src/main/java/ru/ozero/app/ui/ExitNodeResolver.kt")
    }

    @Test
    fun `IP resolve init block uses collectLatest to cancel stale resolution`() {
        val initBlock = mainSource.substringAfter("init {").substringBefore("fun refreshIpInfo")
        val ipResolveSection = initBlock.substringAfter("lastSessionKey: String?")
            .substringBefore("fun onConnectClick")
        assertTrue(
            ipResolveSection.contains("tunnelController.state.collectLatest"),
            "IP resolve must use collectLatest so disconnect cancels in-flight exit-node resolution.",
        )
    }

    @Test
    fun `Result toState rethrows CancellationException`() {
        val body = resolverSource
            .substringAfter("private fun Result<IpInfo>.toState")
            .substringBeforeLast("}")
        assertTrue(
            body.contains("if (it is kotlinx.coroutines.CancellationException) throw it") ||
                body.contains("if (it is CancellationException) throw it"),
            "Result.toState() must rethrow CancellationException instead of converting cancellation to Error.",
        )
    }

    private fun source(path: String): String {
        val moduleRoot = File(System.getProperty("user.dir") ?: ".")
        val file = File(moduleRoot, path)
        assertTrue(file.exists(), "source not found: $file")
        return file.readText()
    }
}
