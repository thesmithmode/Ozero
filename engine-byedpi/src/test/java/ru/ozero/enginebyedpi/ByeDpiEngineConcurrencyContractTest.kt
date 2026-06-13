package ru.ozero.enginebyedpi

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ByeDpiEngineConcurrencyContractTest {

    private val moduleRoot = File(System.getProperty("user.dir") ?: ".")
    private val engineSource by lazy {
        File(moduleRoot, "src/main/java/ru/ozero/enginebyedpi/ByeDpiEngine.kt").readText()
    }

    @Test
    fun `proxyDispatcher uses Dispatchers IO limitedParallelism 1`() {
        val pattern = Regex(
            """Dispatchers\.IO\.limitedParallelism\(\s*1\s*\)""",
        )
        assertTrue(
            pattern.containsMatchIn(engineSource),
            "proxyDispatcher обязан использовать Dispatchers.IO.limitedParallelism(1). " +
                "Сериализует byedpi JNI на ОДИН concurrent dispatch (queue безопасно " +
                "сериализует start/stop sequence), но из общего IO пула — без owned thread, " +
                "без daemon-leak, без обязательного close().",
        )
    }

    @Test
    fun `proxyScope использует proxyDispatcher не сырой Dispatchers IO`() {
        val proxyScopeLines = engineSource.lines().filter {
            it.contains("proxyScope =")
        }

        assertTrue(proxyScopeLines.isNotEmpty(), "proxyScope не найден в ByeDpiEngine.kt")
        assertTrue(
            proxyScopeLines.all { it.contains("proxyDispatcher") && !it.contains("Dispatchers.IO") },
            "proxyScope обязан использовать ограниченный proxyDispatcher, не сырой Dispatchers.IO. " +
                "Текущие строки: $proxyScopeLines. " +
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

    @Test
    fun `start дренирует или ротирует proxyDispatcher после cleanup старого job`() {
        val drainPattern = Regex(
            """withContext\(proxyDispatcher\)\s*\{\s*\}""",
        )
        assertTrue(
            drainPattern.containsMatchIn(engineSource) && engineSource.contains("rotateProxyLane("),
            "start() обязан вызывать withContext(proxyDispatcher) {} после cleanup old job " +
                "и ротировать lane при timeout. Cancelled JNI игнорирует Thread.interrupt() " +
                "и может держать единственный слот dispatcher; без ротации новый proxy coroutine " +
                "встаёт в очередь за ним → waitSocksReady() timeout.",
        )
    }

    @Test
    fun `start не ротирует proxy lane до drain старого dispatcher`() {
        val prematureRotatePattern = Regex(
            """oldJob\.cancel\(\)\s*rotateProxyLane\(\)""",
        )
        val deferredDrainPattern = Regex(
            """else\s+if\s*\(oldJob\s*!=\s*null\)\s*\{\s*drainOrRotateProxyLane\(\)\s*\}""",
        )
        assertFalse(
            prematureRotatePattern.containsMatchIn(engineSource),
            "start() не должен ротировать proxyDispatcher сразу после cancel старого oldJob. " +
                "Иначе drainOrRotateProxyLane() дренирует свежую пустую lane, а старый JNI main() " +
                "может позже сбросить native guard под новым instance.",
        )
        assertTrue(
            deferredDrainPattern.containsMatchIn(engineSource) &&
                engineSource.contains("if (rotateBeforeLaunch)") &&
                engineSource.contains("rotateProxyLane(\"start: previous proxy lane wedged\")") &&
                engineSource.contains("rotateProxyLane(\"stop: proxyJob did not finish within \${STOP_GRACE_MS}ms\")"),
            "start() обязан сначала дренировать dispatcher, который владеет oldJob/hadKnownWedge, " +
                "и только drainOrRotateProxyLane() может ротировать lane после timeout.",
        )
    }

    @Test
    fun `stop использует STOP_GRACE_MS не более 2 секунд`() {
        val pattern = Regex("""const\s+val\s+STOP_GRACE_MS\s*=\s*(\d[\d_]*)L""")
        val match = pattern.find(engineSource)
        assertNotNull(match, "STOP_GRACE_MS не найден в companion object")
        val value = match.groupValues[1].replace("_", "").toLong()
        assertTrue(
            value <= 2_000,
            "STOP_GRACE_MS=$value превышает 2000ms — native thread должен завершиться быстро, " +
                "каскад длинных таймаутов ухудшает restart latency.",
        )
    }
}
