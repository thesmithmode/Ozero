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
    fun `IP resolve init block использует collectLatest — race guard на старый IP после disconnect`() {
        val initBlock = source.substringAfter("init {").substringBefore("fun refreshIpInfo")
        val ipResolveSection = initBlock.substringAfter("lastSessionKey: String?")
            .substringBefore("private fun")
        assertTrue(
            ipResolveSection.contains("tunnelController.state.collectLatest"),
            "IP resolve init block обязан использовать collectLatest, не collect. " +
                "Race: connect → 3s warmup + resolveIpInfoWithRetry в полёте → disconnect → state=Idle сбрасывает " +
                "_ipInfo, старый callback дописывает Loaded поверх Idle. " +
                "collectLatest cancel'ит in-flight resolve на новой emission.",
        )
    }

    @Test
    fun `Result_toState rethrows CancellationException в onFailure`() {
        val body = source
            .substringAfter("private fun Result<IpInfo>.toState")
            .substringBefore("fun onConnectClick")
        assertTrue(
            body.contains("if (it is kotlinx.coroutines.CancellationException) throw it") ||
                body.contains("if (it is CancellationException) throw it"),
            "Result.toState() обязан re-throw CancellationException в onFailure — иначе " +
                "viewModelScope.cancel() превращается в IpInfoState.Error на финальной попытке. " +
                "Body:\n$body",
        )
    }
}
