package ru.ozero.commonnet

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertTrue

class OkHttpIpInfoProviderCancellationTest {

    private val source by lazy {
        val moduleRoot = File(System.getProperty("user.dir") ?: ".")
        val f = File(moduleRoot, "src/main/java/ru/ozero/commonnet/OkHttpIpInfoProvider.kt")
        assertTrue(f.exists(), "OkHttpIpInfoProvider.kt не найден: $f")
        f.readText()
    }

    @Test
    fun `doFetch rethrows CancellationException вместо упаковки в Result_failure`() {
        val body = source.substringAfter("private suspend fun doFetch").substringBefore("private companion object")
        assertTrue(
            body.contains("catch (ce: kotlinx.coroutines.CancellationException)"),
            "doFetch обязан ловить CancellationException отдельно — иначе runCatching/try-catch " +
                "глотает CE и нарушает structured concurrency. Body:\n$body",
        )
        assertTrue(
            body.contains("throw ce"),
            "После catch CancellationException обязан быть throw ce — иначе coroutine cancel " +
                "превращается в Result.failure и UI показывает фейковую ошибку. Body:\n$body",
        )
        assertTrue(
            !body.contains("runCatching {"),
            "doFetch не должен использовать runCatching — он не пере-бросает CancellationException. " +
                "Использовать try/catch с явным catch CE и rethrow.",
        )
    }
}
