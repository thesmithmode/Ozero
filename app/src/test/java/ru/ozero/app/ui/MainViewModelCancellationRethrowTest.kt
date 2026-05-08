package ru.ozero.app.ui

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertTrue

class MainViewModelCancellationRethrowTest {

    private val source by lazy {
        val moduleRoot = File(System.getProperty("user.dir") ?: ".")
        val f = File(moduleRoot, "src/main/java/ru/ozero/app/ui/MainViewModel.kt")
        assertTrue(f.exists(), "MainViewModel.kt не найден: $f")
        f.readText()
    }

    @Test
    fun `fetchIpInfoViaEngine rethrows CancellationException в onFailure`() {
        val body = source
            .substringAfter("private suspend fun fetchIpInfoViaEngine")
            .substringBefore("private fun engineSocksProxy")
        assertTrue(
            body.contains("if (it is kotlinx.coroutines.CancellationException) throw it"),
            "onFailure в fetchIpInfoViaEngine обязан re-throw CancellationException — иначе " +
                "viewModelScope.cancel() превращается в IpInfoState.Error на финальной попытке. " +
                "Body:\n$body",
        )
    }
}
