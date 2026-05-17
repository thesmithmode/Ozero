package ru.ozero.commonvpn

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TunnelStatsLoggerContractTest {

    private val source by lazy {
        val moduleRoot = File(System.getProperty("user.dir") ?: ".")
        val f = File(moduleRoot, "src/main/java/ru/ozero/commonvpn/TunnelStatsLogger.kt")
        assertTrue(f.exists(), "TunnelStatsLogger.kt не найден: $f")
        f.readText()
    }

    private val serviceSource by lazy {
        val moduleRoot = File(System.getProperty("user.dir") ?: ".")
        val f = File(moduleRoot, "src/main/java/ru/ozero/commonvpn/OzeroVpnService.kt")
        assertTrue(f.exists(), "OzeroVpnService.kt не найден: $f")
        f.readText()
    }

    private val coordinatorSource by lazy {
        val moduleRoot = File(System.getProperty("user.dir") ?: ".")
        val f = File(moduleRoot, "src/main/java/ru/ozero/commonvpn/StartSequenceCoordinator.kt")
        assertTrue(f.exists(), "StartSequenceCoordinator.kt не найден: $f")
        f.readText()
    }

    private val shutdownSource by lazy {
        val moduleRoot = File(System.getProperty("user.dir") ?: ".")
        val f = File(moduleRoot, "src/main/java/ru/ozero/commonvpn/ShutdownCoordinator.kt")
        assertTrue(f.exists(), "ShutdownCoordinator.kt не найден: $f")
        f.readText()
    }

    @Test
    fun `companion exposes sampling и notification constants`() {
        assertEquals(1_000L, TunnelStatsLogger.STATS_SAMPLE_INTERVAL_MS)
        assertEquals(30, TunnelStatsLogger.STATS_LOG_EVERY)
        assertEquals(1, TunnelStatsLogger.STATS_NOTIFY_EVERY)
    }

    @Test
    fun `публичный API — start + cancel`() {
        listOf("fun start(", "fun cancel(").forEach { anchor ->
            assertTrue(source.contains(anchor), "Public API anchor потерян: '$anchor'")
        }
    }

    @Test
    fun `start cancels previous job before launch — нет утечек двойного цикла`() {
        val body = source.substringAfter("fun start(").substringBefore("fun cancel(")
        assertTrue(
            body.contains("statsJobRef.getAndSet(null)?.cancel()"),
            "start обязан отменить предыдущий statsJob — иначе double-loop. Body:\n$body",
        )
    }

    @Test
    fun `start использует tun iface stats и uid stats fallback`() {
        val body = source.substringAfter("fun start(").substringBefore("fun cancel(")
        assertTrue(body.contains("TunInterfaceStats.readTunStats(iface)"), "iface stats обязательно.")
        assertTrue(body.contains("UidTrafficStats.read()"), "uid stats fallback обязателен.")
    }

    @Test
    fun `start уважает stopSignal — выход без последней нотификации`() {
        val body = source.substringAfter("fun start(").substringBefore("fun cancel(")
        assertTrue(
            body.contains("if (stopSignal.get()) return@launch"),
            "Early return по stopSignal обязателен — иначе тики после stopVpn ломают teardown. Body:\n$body",
        )
        assertTrue(
            body.contains("!stopSignal.get()"),
            "Notification gate по stopSignal обязателен. Body:\n$body",
        )
    }

    @Test
    fun `start логирует warn если ни iface ни uid stats недоступны`() {
        val body = source.substringAfter("fun start(").substringBefore("fun cancel(")
        assertTrue(
            body.contains("ни iface, ни uid stats недоступны"),
            "Warn при отсутствии stats обязателен — иначе тихая потеря данных. Body:\n$body",
        )
    }

    @Test
    fun `start пишет TunnelStats через tunnelController updateStats`() {
        val body = source.substringAfter("fun start(").substringBefore("fun cancel(")
        assertTrue(
            body.contains("tunnelController.updateStats(snapshot)"),
            "updateStats обязателен — UI читает state через tunnelController.stats. Body:\n$body",
        )
    }

    @Test
    fun `start форматирует notification через NotificationStatsFormatter + engineExtras callback`() {
        val body = source.substringAfter("fun start(").substringBefore("fun cancel(")
        assertTrue(
            body.contains("NotificationStatsFormatter.format(stats, engineExtras())"),
            "Notification формируется через formatter + engineExtras callback. Body:\n$body",
        )
    }

    @Test
    fun `CancellationException пробрасывается, не глотается`() {
        val body = source.substringAfter("fun start(").substringBefore("fun cancel(")
        assertTrue(
            body.contains("CancellationException") && body.contains("throw ce"),
            "CancellationException обязан re-throw — иначе coroutine cancel ломается. Body:\n$body",
        )
    }

    @Test
    fun `cancel зачищает statsJobRef`() {
        val body = source.substringAfter("fun cancel(").substringBefore("companion object")
        assertTrue(
            body.contains("statsJobRef.getAndSet(null)?.cancel()"),
            "cancel обязан очищать statsJobRef. Body:\n$body",
        )
    }

    @Test
    fun `logger не зависит от OzeroVpnService — тестируемость`() {
        assertTrue(
            !source.contains("OzeroVpnService"),
            "TunnelStatsLogger не должен ссылаться на OzeroVpnService — нарушение модульности.",
        )
        val classDecl = source.substringAfter("class TunnelStatsLogger").substringBefore("{").trim()
        assertTrue(
            classDecl.contains("scope: CoroutineScope") &&
                classDecl.contains("engineExtras: () -> String"),
            "Logger зависит от scope + engineExtras callback, не от service напрямую.",
        )
    }

    @Test
    fun `OzeroVpnService строит TunnelStatsLogger через by lazy`() {
        assertTrue(
            serviceSource.contains("statsLogger: TunnelStatsLogger by lazy {") &&
                serviceSource.contains("TunnelStatsLogger("),
            "OzeroVpnService обязан инжектить statsLogger через by lazy.",
        )
    }

    @Test
    fun `OzeroVpnService больше не имеет inline startStatsLogger`() {
        assertTrue(
            !serviceSource.contains("private fun startStatsLogger"),
            "startStatsLogger обязан быть удалён из сервиса — извлечён в TunnelStatsLogger.",
        )
        assertTrue(
            !serviceSource.contains("STATS_SAMPLE_INTERVAL_MS") &&
                !serviceSource.contains("STATS_LOG_EVERY") &&
                !serviceSource.contains("STATS_NOTIFY_EVERY"),
            "STATS_* константы обязаны быть только в TunnelStatsLogger.",
        )
    }

    @Test
    fun `runStartSequence вызывает statsLogger start`() {
        val body = coordinatorSource
            .substringAfter("suspend fun run()")
            .substringBefore("suspend fun engineNeedsCustomTun")
        assertTrue(
            body.contains("statsLogger.start()") || body.contains("deps.statsLogger.start()"),
            "StartSequenceCoordinator.run() обязан стартовать stats logger. Body:\n$body",
        )
    }

    @Test
    fun `stopVpn и performShutdown вызывают statsLogger cancel`() {
        val stopBody = shutdownSource
            .substringAfter("fun stopVpn()")
            .substringBefore("suspend fun performShutdown(")
        assertTrue(
            stopBody.contains("statsLogger.cancel()") || stopBody.contains("deps.statsLogger.cancel()"),
            "stopVpn обязан отменить stats logger. Body:\n$stopBody",
        )
        val shutdownBody = shutdownSource
            .substringAfter("suspend fun performShutdown(")
            .substringBefore("private fun recordSessionEnd")
        assertTrue(
            shutdownBody.contains("statsLogger.cancel()") || shutdownBody.contains("deps.statsLogger.cancel()"),
            "performShutdown обязан отменить stats logger. Body:\n$shutdownBody",
        )
    }
}
