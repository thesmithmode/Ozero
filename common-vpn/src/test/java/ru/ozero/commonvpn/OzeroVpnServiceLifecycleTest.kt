package ru.ozero.commonvpn

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OzeroVpnServiceLifecycleTest {

    private val source by lazy {
        val moduleRoot = File(System.getProperty("user.dir") ?: ".")
        val f = File(moduleRoot, "src/main/java/ru/ozero/commonvpn/OzeroVpnService.kt")
        assertTrue(f.exists(), "OzeroVpnService.kt не найден: $f")
        f.readText()
    }

    @Test
    fun `onCreate переопределён и вызывает super`() {
        assertTrue(source.contains("override fun onCreate()"))
        val body = source.substringAfter("override fun onCreate()").substringBefore("private val tunFdRef")
        assertTrue(body.contains("super.onCreate()"))
    }

    @Test
    fun `onCreate логирует before и after super`() {
        val body = source.substringAfter("override fun onCreate()").substringBefore("private val tunFdRef")
        val beforeIdx = body.indexOf("onCreate before super")
        val superIdx = body.indexOf("super.onCreate()")
        val afterIdx = body.indexOf("onCreate after super")
        assertTrue(beforeIdx in 0 until superIdx && superIdx < afterIdx)
    }

    @Test
    fun `onStartCommand имеет entry-log`() {
        val body = source.substringAfter("override fun onStartCommand").substringBefore("private fun startVpn()")
        assertTrue(body.contains("onStartCommand action="))
    }

    @Test
    fun `onStartCommand guard на chainOrchestrator возвращает START_NOT_STICKY`() {
        val body = source.substringAfter("override fun onStartCommand").substringBefore("private fun startVpn()")
        assertTrue(body.contains("::chainOrchestrator.isInitialized"))
        val guardBlock = body
            .substringAfter("::chainOrchestrator.isInitialized")
            .substringBefore("when (intent?.action)")
        assertTrue(guardBlock.contains("stopSelf(startId)"))
        assertTrue(guardBlock.contains("START_NOT_STICKY"))
    }

    @Test
    fun `startVpn preload-ит hev TProxyService на main thread до serviceScope launch`() {
        val body = source.substringAfter("private fun startVpn()").substringBefore("private fun stopVpn()")
        val preloadIdx = body.indexOf("hev.TProxyService.loadOnce()")
        val launchIdx = body.indexOf("serviceScope.launch")
        assertTrue(
            preloadIdx in 0 until launchIdx,
            "startVpn обязан вызвать hev.TProxyService.loadOnce() ДО serviceScope.launch — " +
                "loadLibrary на coroutine worker thread триггерит SIGSEGV в vendor libglnubia.so " +
                "(nubia::Messager::timerLoop) на Nubia/RedMagic. Preload на main thread обходит race " +
                "vendor performance monitor с loader callback.",
        )
    }

    @Test
    fun `startVpn preload логирует thread name и main looper`() {
        val body = source.substringAfter("private fun startVpn()").substringBefore("private fun stopVpn()")
        val preloadIdx = body.indexOf("hev.TProxyService.loadOnce()")
        check(preloadIdx >= 0) { "preload missing" }
        val window = body.substring(maxOf(0, preloadIdx - 500), preloadIdx)
        assertTrue(window.contains("loadOnce begin"))
        assertTrue(window.contains("Thread.currentThread().name"))
        assertTrue(window.contains("Looper.myLooper()") && window.contains("Looper.getMainLooper()"))
    }

    @Test
    fun `startVpn preload логирует libraryLoaded после loadOnce`() {
        val body = source.substringAfter("private fun startVpn()").substringBefore("private fun stopVpn()")
        val preloadIdx = body.indexOf("hev.TProxyService.loadOnce()")
        check(preloadIdx >= 0) { "preload missing" }
        val tail = body.substring(preloadIdx, minOf(body.length, preloadIdx + 800))
        assertTrue(tail.contains("loadOnce done"))
        assertTrue(tail.contains("libraryLoaded="))
    }

    @Test
    fun `performShutdown stop order — chainOrchestrator ПЕРЕД tunnelGateway`() {
        val body = source.substringAfter("private suspend fun performShutdown(")
            .substringBefore("internal fun buildTunBuilder")
        val chainIdx = body.indexOf("chainOrchestrator.stop()")
        val nativeIdx = body.indexOf("tunnelGateway.stop()")
        assertTrue(
            chainIdx in 0 until nativeIdx,
            "chainOrchestrator.stop() (byedpi) ОБЯЗАН быть ПЕРЕД tunnelGateway.stop() (libhev). " +
                "Иначе libhev event-loop ждёт outstanding socks5 connections к byedpi → " +
                "TProxyStopService зависает > 3000ms → abandoned thread → reconnect deadlock на " +
                "libhev singleton.",
        )
    }

    @Test
    fun `performShutdown закрывает TUN fd ПОСЛЕ tunnelGateway_stop в finally`() {
        val body = source.substringAfter("private suspend fun performShutdown(")
            .substringBefore("internal fun buildTunBuilder")
        val nativeIdx = body.indexOf("tunnelGateway.stop()")
        val closeIdx = body.indexOf("tunFdRef.getAndSet(null)?.close()")
        assertTrue(
            nativeIdx in 0 until closeIdx,
            "tunFd закрывается ПОСЛЕ tunnelGateway.stop() (libhev). Сначала kill byedpi → " +
                "libhev завершается на ECONNREFUSED → потом native stop → потом close fd.",
        )
    }

    @Test
    fun `stopVpn не force-killит процесс`() {
        val body = source.substringAfter("private fun stopVpn()").substringBefore("private suspend fun performShutdown")
        assertFalse(body.contains("processKiller.kill(Process.myPid())"))
    }

    @Test
    fun `onDestroy не force-killит процесс`() {
        val body = source.substringAfter("override fun onDestroy()").substringBefore("private fun enterForegroundOrLog")
        assertFalse(body.contains("processKiller.kill(Process.myPid())"))
    }

    @Test
    fun `buildNotification содержит Stop action с ACTION_STOP intent`() {
        val body = source.substringAfter("private fun buildNotification").substringBefore("override fun onRevoke")
        assertTrue(
            body.contains("ACTION_STOP"),
            "notification обязан содержать ACTION_STOP intent — иначе юзер не может остановить VPN " +
                "из notification shade без открытия app",
        )
        assertTrue(body.contains("addAction"), "notification обязан содержать addAction для Stop кнопки")
        assertTrue(
            body.contains("PendingIntent.getService") || body.contains("getService"),
            "Stop action должен использовать PendingIntent.getService — direct call в OzeroVpnService.onStartCommand",
        )
        assertTrue(
            body.contains("FLAG_IMMUTABLE"),
            "PendingIntent с Android 12+ обязан FLAG_IMMUTABLE",
        )
    }

    @Test
    fun `onRevoke переопределён и вызывает stopVpn`() {
        val body = source.substringAfter("override fun onRevoke()").substringBefore("override fun onDestroy()")
        assertTrue(body.contains("stopVpn()"))
        assertTrue(body.contains("super.onRevoke()"))
    }

    @Test
    fun `performShutdown использует stopSelf с lastStopStartId — не голый stopSelf`() {
        val body = source.substringAfter("private suspend fun performShutdown(")
            .substringBefore("internal fun buildTunBuilder")
        assertTrue(
            body.contains("stopSelf(lastStopStartId.get())"),
            "performShutdown обязан вызывать stopSelf(lastStopStartId.get()), а не безаргументный stopSelf(). " +
                "stopSelf(startId) — no-op если пришёл новый intents, что исключает onDestroy при engine switch. " +
                "Голый stopSelf() безусловно планирует onDestroy → ForegroundServiceDidNotStartInTimeException.",
        )
        assertFalse(
            body.contains(Regex("(?<!lastStopStartId\\.get\\(\\))\\bstopSelf\\(\\)")),
            "performShutdown не должен содержать голый stopSelf() без аргумента",
        )
    }

    @Test
    fun `onDestroy вызывает performShutdown с callStopSelf=false`() {
        val body = source.substringAfter("override fun onDestroy()").substringBefore("private fun enterForegroundOrLog")
        assertTrue(
            body.contains("performShutdown(callStopSelf = false)"),
            "onDestroy должен передавать callStopSelf=false — сервис уже умирает, " +
                "второй stopSelf() из onDestroy-пути не нужен и вреден.",
        )
    }

    @Test
    fun `ACTION_STOP сохраняет startId в lastStopStartId перед stopVpn`() {
        val body = source.substringAfter("override fun onStartCommand").substringBefore("private fun startVpn()")
        val stopBlock = body.substringAfter("ACTION_STOP").substringBefore("ACTION_START")
        assertTrue(
            stopBlock.contains("lastStopStartId.set(startId)"),
            "При ACTION_STOP обязан сохранить startId в lastStopStartId до вызова stopVpn(), " +
                "иначе performShutdown не знает какой startId передать в stopSelf(startId).",
        )
    }

    @Test
    fun `startStatsLogger вызывается безусловно независимо от engine`() {
        val body = source
            .substringAfter("runCatching { healthMonitor.start")
            .substringBefore("private suspend fun engineNeedsCustomTun")
        assertTrue(
            body.contains("startStatsLogger()"),
            "startStatsLogger должен вызываться без условия engineNeedsCustomTun",
        )
        assertFalse(
            body.contains("if (!engineNeedsCustomTun") || body.contains("if (engineNeedsCustomTun"),
            "Не должно быть гейтa engineNeedsCustomTun перед startStatsLogger",
        )
    }

    @Test
    fun `startStatsLogger не вызывает TProxyGetStats — единый источник TunInterfaceStats`() {
        val body = source
            .substringAfter("private fun startStatsLogger()")
            .substringBefore("private fun updateNotificationWithStats")
        assertFalse(
            body.contains("TProxyGetStats"),
            "TProxyGetStats — libhev-only API. Использовать TunInterfaceStats для всех движков",
        )
        assertTrue(
            body.contains("TunInterfaceStats.readTunStats"),
            "stats logger обязан читать через TunInterfaceStats.readTunStats",
        )
    }

    @Test
    fun `tunIfaceNameRef объявлен AtomicReference String`() {
        assertTrue(
            source.contains("private val tunIfaceNameRef = AtomicReference<String?>(null)"),
            "Имя tun-интерфейса хранится в AtomicReference для thread-safe доступа из stats coroutine",
        )
    }

    @Test
    fun `tunIfaceNameRef сбрасывается в null в performShutdown finally`() {
        val body = source
            .substringAfter("private suspend fun performShutdown(")
            .substringBefore("internal fun buildTunBuilder")
        val finallyBlock = body.substringAfter("} finally {").substringBefore("if (callStopSelf)")
        assertTrue(
            finallyBlock.contains("tunIfaceNameRef.set(null)"),
            "performShutdown finally обязан сбрасывать tunIfaceNameRef — иначе stale имя в новой сессии вернёт чужие байты",
        )
    }

    @Test
    fun `establishTun сохраняет tunIfaceNameRef после establish — оба пути`() {
        assertTrue(
            source.contains("captureTunIfaceName(before)"),
            "После establish() обоих путей (engineTun и обычный) должен вызываться captureTunIfaceName",
        )
        val callCount = source.split("captureTunIfaceName(before)").size - 1
        assertTrue(
            callCount >= 2,
            "captureTunIfaceName должен вызываться в обоих establish — establishTun и establishTunForEngine. Найдено: $callCount",
        )
    }

    @Test
    fun `ACTION_START сбрасывает stopping до вызова startVpn`() {
        val body = source.substringAfter("override fun onStartCommand").substringBefore("private fun startVpn()")
        val startBlock = body.substringAfter("ACTION_START, null ->").substringBefore("}")
        assertTrue(
            startBlock.contains("stopping.set(false)"),
            "При ACTION_START обязан сбросить stopping=false до startVpn(), иначе если новый START " +
                "пришёл во время shutdown, startVpn() видит stopping=true и вызывает stopVpn() повторно.",
        )
    }

    @Test
    fun `startVpn не блокирует main thread runBlocking-ом`() {
        val body = source.substringAfter("private fun startVpn()").substringBefore("private fun startStatsLogger")
        assertFalse(
            body.contains("runBlocking"),
            "startVpn не должен использовать runBlocking. settings и split-packages читаются " +
                "из serviceScope.launch (IO) через withTimeoutOrNull + first()/activePackages().",
        )
    }

    @Test
    fun `startVpn читает свежие settings и split-packages из IO scope`() {
        val body = source.substringAfter("private fun startVpn()").substringBefore("private fun startStatsLogger")
        assertTrue(
            body.contains("settingsRepository.settings.first()"),
            "startVpn обязан дочитывать settings.first() свежими — cold-start defaults сломают " +
                "custom DNS / split / hosts / winning-args.",
        )
        assertTrue(
            body.contains("splitTunnelRulesProvider.activePackages()"),
            "startVpn обязан читать activePackages() свежими на каждый connect — иначе изменение " +
                "split-rules видно только со второго reconnect.",
        )
        assertTrue(
            body.contains("withTimeoutOrNull"),
            "Чтение settings/split обязано быть под withTimeoutOrNull — DataStore corruption не " +
                "должен зависать VPN start.",
        )
    }

    @Test
    fun `onDestroy ограничивает runBlocking shutdown через withTimeoutOrNull`() {
        val body = source.substringAfter("override fun onDestroy()").substringBefore("private fun enterForegroundOrLog")
        assertTrue(
            body.contains("runBlocking"),
            "onDestroy обязан runBlocking shutdown — иначе coroutines умрут до завершения performShutdown",
        )
        assertTrue(
            body.contains("withTimeoutOrNull"),
            "runBlocking в onDestroy обязан withTimeoutOrNull — performShutdown имеет 2 внутренних " +
                "таймаута по 3s (chain + native). Без внешнего лимита onDestroy может занять до 6s+, " +
                "ANR threshold = 5s → краш системой как ANR. " +
                "Лимит должен быть < 5000ms.",
        )
        val rbBody = body.substringAfter("runBlocking").take(200)
        assertTrue(
            rbBody.contains("withTimeoutOrNull"),
            "withTimeoutOrNull должен быть ВНУТРИ runBlocking, не снаружи",
        )
    }

    @Test
    fun `onDestroy логирует если runBlocking shutdown не уложился в таймаут`() {
        val body = source.substringAfter("override fun onDestroy()").substringBefore("private fun enterForegroundOrLog")
        assertTrue(
            body.contains("onDestroy shutdown timeout"),
            "Если withTimeoutOrNull вернул null — обязан появиться лог 'onDestroy shutdown timeout' " +
                "для post-mortem диагностики ANR-near-miss",
        )
    }

    @Test
    fun `runStartSequence использует pickActiveEngine не hardcoded BYEDPI fallback`() {
        val body = source.substringAfter("private suspend fun runStartSequence()")
            .substringBefore("private suspend fun engineNeedsCustomTun")
        assertTrue(
            body.contains("pickActiveEngine("),
            "runStartSequence обязан звать pickActiveEngine для авто/ручного выбора — " +
                "не hardcoded ?: EngineId.BYEDPI",
        )
        assertFalse(
            body.contains("?: EngineId.BYEDPI"),
            "hardcoded fallback на BYEDPI запрещён — выбор движка должен идти через " +
                "engineAutoPriority в pickActiveEngine",
        )
    }

    @Test
    fun `pickActiveEngine итерирует engineAutoPriority при manualEngine null`() {
        val body = source.substringAfter("private fun pickActiveEngine(")
            .substringBefore("private fun establishTun(")
        assertTrue(
            body.contains("engineAutoPriority"),
            "pickActiveEngine обязан читать settings.engineAutoPriority для авто-режима",
        )
        assertTrue(
            body.contains("DEFAULT_ENGINE_AUTO_PRIORITY"),
            "fallback на DEFAULT_ENGINE_AUTO_PRIORITY когда settings null обязателен",
        )
        assertTrue(
            body.contains("buildEngineConfig("),
            "pickActiveEngine обязан проверять buildEngineConfig — первый non-null = выбор",
        )
    }

    @Test
    fun `onDestroy не shutdown-ит singleton HealthMonitor`() {
        val body = source.substringAfter("override fun onDestroy()").substringBefore("private fun enterForegroundOrLog")
        assertFalse(
            body.contains("healthMonitor.shutdown()"),
            "HealthMonitor — @Singleton, его внутренний scope живёт всё время процесса. shutdown() " +
                "в onDestroy сделает scope cancelled навсегда → следующий connect не запустит probe → " +
                "DEGRADED badge никогда не покажется. Используй stop().",
        )
        assertTrue(
            body.contains("healthMonitor.stop()"),
            "onDestroy обязан вызвать healthMonitor.stop() — сбросит status в UNKNOWN и cancel " +
                "текущий probe job, не убивая сам scope.",
        )
    }
}
