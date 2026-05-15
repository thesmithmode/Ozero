package ru.ozero.enginetelegram

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertTrue

class TelegramProxyServiceConcurrencyContractTest {

    private val source by lazy {
        val moduleRoot = File(System.getProperty("user.dir") ?: ".")
        val f = File(moduleRoot, "src/main/java/ru/ozero/enginetelegram/TelegramProxyService.kt")
        assertTrue(f.exists(), "TelegramProxyService.kt не найден: $f")
        f.readText()
    }

    @Test
    fun `start() сериализует runProxy через runMutex withLock — защита от двойного startProxy`() {
        assertTrue(
            source.contains("private val runMutex = Mutex()"),
            "runMutex обязан существовать — без него двойной start() параллельно дёргает " +
                "wrapper.startProxy → второй mtg-процесс пытается bind() того же порта → EADDRINUSE.",
        )
        assertTrue(
            source.contains("runMutex.withLock { runProxy("),
            "start() обязан оборачивать runProxy в runMutex.withLock — иначе concurrent start " +
                "при flicker tunnelState даст port conflict + zombie process.",
        )
    }

    @Test
    fun `runProxy уничтожает старый process через getAndSet перед set нового`() {
        val runProxyBlock = source.substringAfter("private suspend fun runProxy")
            .substringBefore("private fun killProcess")
        assertTrue(
            runProxyBlock.contains("processRef.getAndSet(newProcess)") &&
                runProxyBlock.contains("destroyForcibly"),
            "runProxy обязан processRef.getAndSet(newProcess) с destroyForcibly для старого process. " +
                "Чистый processRef.set перезаписывает ссылку, теряя старый Process → zombie до OOM-kill.",
        )
    }
}
