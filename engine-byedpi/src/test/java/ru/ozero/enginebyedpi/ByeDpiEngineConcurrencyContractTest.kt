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
    fun `proxyDispatcher = Dispatchers IO limitedParallelism 1`() {
        val pattern = Regex(
            """proxyDispatcher\s*=\s*Dispatchers\.IO\.limitedParallelism\(\s*1\s*\)""",
        )
        assertTrue(
            pattern.containsMatchIn(engineSource),
            "proxyDispatcher обязан быть Dispatchers.IO.limitedParallelism(1). " +
                "Сериализует byedpi JNI на ОДИН concurrent dispatch (queue безопасно " +
                "сериализует start/stop sequence), но из общего IO пула — без owned thread, " +
                "без daemon-leak, без обязательного close().",
        )
    }

    @Test
    fun `proxyScope использует proxyDispatcher не сырой Dispatchers IO`() {
        val proxyScopeLine = engineSource.lines().firstOrNull { it.contains("val proxyScope") }
            ?: error("val proxyScope не найден в ByeDpiEngine.kt")

        assertTrue(
            proxyScopeLine.contains("proxyDispatcher") && !proxyScopeLine.contains("Dispatchers.IO"),
            "proxyScope обязан использовать ограниченный proxyDispatcher, не сырой Dispatchers.IO. " +
                "Текущая строка: $proxyScopeLine. " +
                "Без bound: блокирующий JNI после coroutine.cancel() может занять до 64 IO " +
                "потоков (cap пула) при restart storm.",
        )
    }

    @Test
    fun `JNI_GUARD_BUSY константа объявлена в companion object`() {
        val pattern = Regex(
            """const\s+val\s+JNI_GUARD_BUSY\s*=\s*-2""",
        )
        assertTrue(
            pattern.containsMatchIn(engineSource),
            "JNI_GUARD_BUSY = -2 обязан быть const в companion object. " +
                "Должен совпадать с #define JNI_GUARD_BUSY в native-lib.c — " +
                "Kotlin layer различает guard busy от real failure (-1).",
        )
    }

    @Test
    fun `startProxyWithRecovery вызывает emergencyReset на JNI_GUARD_BUSY`() {
        val pattern = Regex(
            """if\s*\(code\s*!=\s*JNI_GUARD_BUSY\)\s*return\s+code""" +
                """[^}]*proxy\.emergencyReset\(\)""",
            RegexOption.DOT_MATCHES_ALL,
        )
        assertTrue(
            pattern.containsMatchIn(engineSource),
            "startProxyWithRecovery обязан вызывать proxy.emergencyReset() при code == JNI_GUARD_BUSY. " +
                "Иначе wedged old main() оставит engine permanent unusable до process restart.",
        )
    }

    @Test
    fun `start обрабатывает JNI_GUARD_BUSY отдельной веткой логирования`() {
        val pattern = Regex(
            """code\s*==\s*JNI_GUARD_BUSY[^"]*"[^"]*guard busy""",
            RegexOption.DOT_MATCHES_ALL,
        )
        assertTrue(
            pattern.containsMatchIn(engineSource),
            "start() обязан различать JNI_GUARD_BUSY (warn) от прочих non-zero codes (error). " +
                "Без discrimination guard busy выглядит как real failure → ложные алерты в логах.",
        )
    }
}
