package ru.ozero.enginebyedpi

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertTrue

class ByeDpiEngineConcurrencyContractTest {

    private val moduleRoot = File(System.getProperty("user.dir") ?: ".")
    private val engineSource by lazy {
        File(moduleRoot, "src/main/java/ru/ozero/enginebyedpi/ByeDpiEngine.kt").readText()
    }

    @Test
    fun `proxyScope использует dedicated dispatcher не Dispatchers IO`() {
        val proxyScopeLine = engineSource.lines().firstOrNull { it.contains("val proxyScope") }
            ?: error("val proxyScope не найден в ByeDpiEngine.kt")

        assertTrue(
            !proxyScopeLine.contains("Dispatchers.IO"),
            "proxyScope обязан использовать dedicated dispatcher, не Dispatchers.IO. " +
                "Текущая строка: $proxyScopeLine. " +
                "Dispatchers.IO leak'ает потоки в общем пуле (cap 64) когда Kotlin oldJob.cancel() " +
                "не убивает блокирующий JNI — старый поток остаётся занят upstream main() пока " +
                "тот не вернётся. Повторение → исчерпание пула.",
        )
    }

    @Test
    fun `proxyDispatcher объявлен как single-thread Executor с named thread`() {
        val pattern = Regex(
            """proxyDispatcher\s*=\s*Executors\.newSingleThreadExecutor\s*\{""" +
                """[^}]*Thread\([^)]+,\s*"byedpi-proxy"\)""",
            RegexOption.DOT_MATCHES_ALL,
        )
        assertTrue(
            pattern.containsMatchIn(engineSource),
            "proxyDispatcher обязан быть Executors.newSingleThreadExecutor с named thread " +
                "\"byedpi-proxy\" — даёт observable утечку в logcat/profiler если упадёт, " +
                "и изолирует JNI блок от общего IO пула.",
        )
    }

    @Test
    fun `proxyDispatcher thread помечен isDaemon true`() {
        val pattern = Regex(
            """Thread\([^)]+,\s*"byedpi-proxy"\)\.apply\s*\{[^}]*isDaemon\s*=\s*true""",
            RegexOption.DOT_MATCHES_ALL,
        )
        assertTrue(
            pattern.containsMatchIn(engineSource),
            "byedpi-proxy thread обязан быть isDaemon=true — иначе блок'нутый JNI " +
                "удержит JVM от завершения процесса.",
        )
    }
}
