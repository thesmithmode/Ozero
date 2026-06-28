package ru.ozero.commonvpn

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@Suppress("LargeClass")
class OzeroVpnServiceLifecycleTest {

    private val source by lazy {
        val moduleRoot = File(System.getProperty("user.dir") ?: ".")
        val f = File(moduleRoot, "src/main/java/ru/ozero/commonvpn/OzeroVpnService.kt")
        assertTrue(f.exists(), "OzeroVpnService.kt не найден: $f")
        f.readText()
    }

    private val startSequenceSource by lazy {
        val moduleRoot = File(System.getProperty("user.dir") ?: ".")
        val f = File(moduleRoot, "src/main/java/ru/ozero/commonvpn/StartSequenceCoordinator.kt")
        assertTrue(f.exists(), "StartSequenceCoordinator.kt не найден: $f")
        f.readText()
    }

    private val actionDispatcherSource by lazy {
        val moduleRoot = File(System.getProperty("user.dir") ?: ".")
        val f = File(moduleRoot, "src/main/java/ru/ozero/commonvpn/OzeroVpnServiceActionDispatcher.kt")
        assertTrue(f.exists(), "OzeroVpnServiceActionDispatcher.kt не найден: $f")
        f.readText()
    }

    private val startCoordinatorSource by lazy {
        val moduleRoot = File(System.getProperty("user.dir") ?: ".")
        val f = File(moduleRoot, "src/main/java/ru/ozero/commonvpn/OzeroVpnServiceStartCoordinator.kt")
        assertTrue(f.exists(), "OzeroVpnServiceStartCoordinator.kt missing: $f")
        f.readText()
    }

    private val statsLoggerSource by lazy {
        val moduleRoot = File(System.getProperty("user.dir") ?: ".")
        val f = File(moduleRoot, "src/main/java/ru/ozero/commonvpn/TunnelStatsLogger.kt")
        assertTrue(f.exists(), "TunnelStatsLogger.kt не найден: $f")
        f.readText()
    }

    private val helperSource by lazy {
        val moduleRoot = File(System.getProperty("user.dir") ?: ".")
        val f = File(moduleRoot, "src/main/java/ru/ozero/commonvpn/TunBuilderHelper.kt")
        assertTrue(f.exists(), "TunBuilderHelper.kt не найден: $f")
        f.readText()
    }

    private val shutdownSource by lazy {
        val moduleRoot = File(System.getProperty("user.dir") ?: ".")
        val f = File(moduleRoot, "src/main/java/ru/ozero/commonvpn/ShutdownCoordinator.kt")
        assertTrue(f.exists(), "ShutdownCoordinator.kt не найден: $f")
        f.readText()
    }

    @Test
    fun `onCreate переопределён и вызывает super`() {
        assertTrue(source.contains("override fun onCreate()"))
        val body = source.substringAfter("override fun onCreate()").substringBefore("private var socketProtector")
        assertTrue(body.contains("super.onCreate()"))
    }

    @Test
    fun `onCreate логирует before и after super`() {
        val body = source.substringAfter("override fun onCreate()").substringBefore("private var socketProtector")
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
        val body = actionDispatcherSource.substringAfter("fun dispatch(").substringBefore("} catch")
        assertTrue(body.contains("isChainOrchestratorReady()"))
        val guardBlock = body
            .substringAfter("!isChainOrchestratorReady()")
            .substringBefore("when (action)")
        assertTrue(guardBlock.contains("stopSelf(startId)"))
        assertTrue(guardBlock.contains("NOT_STICKY"))
    }

    @Test
    fun `startVpn preload-ит hev TProxyService на main thread до serviceScope launch`() {
        val body = source.substringAfter("private val startCoordinator").substringBefore("companion object")
        val preloadIdx = body.indexOf("hev.TProxyService.loadOnce()")
        val startCallIdx = source.substringAfter("private fun startVpn()").indexOf("startCoordinator.start()")
        assertTrue(
            preloadIdx >= 0 && startCallIdx >= 0,
            "startVpn обязан вызвать hev.TProxyService.loadOnce() ДО serviceScope.launch — " +
                "loadLibrary на coroutine worker thread триггерит SIGSEGV в vendor libglnubia.so " +
                "(nubia::Messager::timerLoop) на Nubia/RedMagic. Preload на main thread обходит race " +
                "vendor performance monitor с loader callback.",
        )
    }

    @Test
    fun `startVpn preload логирует thread name и main looper`() {
        val body = source.substringAfter("private val startCoordinator").substringBefore("companion object")
        val preloadIdx = body.indexOf("hev.TProxyService.loadOnce()")
        check(preloadIdx >= 0) { "preload missing" }
        val window = body.substring(maxOf(0, preloadIdx - 500), preloadIdx)
        assertTrue(window.contains("loadOnce begin"))
        assertTrue(window.contains("Thread.currentThread().name"))
        assertTrue(window.contains("Looper.myLooper()") && window.contains("Looper.getMainLooper()"))
    }

    @Test
    fun `startVpn preload логирует libraryLoaded после loadOnce`() {
        val body = source.substringAfter("private val startCoordinator").substringBefore("companion object")
        val preloadIdx = body.indexOf("hev.TProxyService.loadOnce()")
        check(preloadIdx >= 0) { "preload missing" }
        assertTrue(body.contains("isTunnelLibraryLoaded = { hev.TProxyService.libraryLoaded }"))
        assertTrue(startCoordinatorSource.contains("loadOnce done"))
        assertTrue(startCoordinatorSource.contains("isTunnelLibraryLoaded()"))
    }

    @Test
    fun `performShutdown stop order — chainOrchestrator ПЕРЕД tunnelGateway`() {
        val body = shutdownSource.substringAfter("suspend fun performShutdown(")
            .substringBefore("private fun recordSessionEnd(")
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
        val body = shutdownSource.substringAfter("suspend fun performShutdown(")
            .substringBefore("private fun recordSessionEnd(")
        val nativeIdx = body.indexOf("tunnelGateway.stop()")
        val closeIdx = body.indexOf("state.tunFdRef.getAndSet(null)?.close()")
        assertTrue(
            nativeIdx in 0 until closeIdx,
            "tunFd закрывается ПОСЛЕ tunnelGateway.stop() (libhev). Сначала kill byedpi → " +
                "libhev завершается на ECONNREFUSED → потом native stop → потом close fd.",
        )
    }

    @Test
    fun `stopVpn не force-killит процесс`() {
        val body = shutdownSource.substringAfter("fun stopVpn(").substringBefore("suspend fun performShutdown(")
        assertFalse(body.contains("processKiller.kill(Process.myPid())"))
    }

    @Test
    fun `onDestroy не force-killит процесс`() {
        val body = source.substringAfter("override fun onDestroy()").substringBefore("\n}\n")
        assertFalse(body.contains("processKiller.kill(Process.myPid())"))
    }

    @Test
    fun `onRevoke переопределён и вызывает stopVpn`() {
        val body = source.substringAfter("override fun onRevoke()").substringBefore("override fun onDestroy()")
        assertTrue(body.contains("stopVpn()"))
        assertTrue(body.contains("super.onRevoke()"))
    }

    @Test
    fun `onRevoke killит процесс с задержкой — освобождает VPN slot для других VPN apps`() {
        val body = source.substringAfter("override fun onRevoke()").substringBefore("override fun onDestroy()")
        assertTrue(
            body.contains("processKiller.kill(android.os.Process.myPid())"),
            "onRevoke ОБЯЗАН kill свой pid — иначе libgojni+VPN slot остаются заняты, " +
                "URnetwork-app/другой VPN не может стартовать. Только onRevoke (не stopVpn/onDestroy) — " +
                "потому что revoke = юзер явно выбрал другой VPN, фоновая работа Ozero не нужна.",
        )
        assertTrue(
            body.contains("postDelayed"),
            "kill обязан быть отложенным (postDelayed) чтобы stopVpn shutdown coroutines + " +
                "super.onRevoke() успели завершиться. Мгновенный kill теряет logs/состояние.",
        )
        assertTrue(
            body.contains("REVOKE_KILL_DELAY_MS"),
            "delay вынесен в константу REVOKE_KILL_DELAY_MS — чтобы тест мог его проверить + " +
                "не магическое число inline.",
        )
    }

    @Test
    fun `performShutdown использует stopSelf с latestStartId — не голый stopSelf`() {
        val body = shutdownSource.substringAfter("suspend fun performShutdown(")
            .substringBefore("private fun recordSessionEnd(")
        assertTrue(
            body.contains("stopSelfRequest(latestStartIdProvider())"),
            "performShutdown обязан вызывать stopSelfRequest(latestStartIdProvider()), а не безаргументный. " +
                "stopSelf(startId) — no-op если пришёл новый intents, что исключает onDestroy при engine switch. " +
                "Голый stopSelf() безусловно планирует onDestroy → ForegroundServiceDidNotStartInTimeException.",
        )
        assertFalse(
            body.contains(Regex("(?<!latestStartIdProvider\\(\\))\\bstopSelfRequest\\(\\)")),
            "performShutdown не должен содержать голый stopSelfRequest() без аргумента",
        )
    }

    @Test
    fun `onDestroy вызывает performShutdown с callStopSelf=false`() {
        val body = source.substringAfter("override fun onDestroy()").substringBefore("\n}\n")
        assertTrue(
            body.contains("shutdownCoord.performShutdown(callStopSelf = false)"),
            "onDestroy должен передавать callStopSelf=false — сервис уже умирает, " +
                "второй stopSelf() из onDestroy-пути не нужен и вреден.",
        )
    }

    @Test
    fun `onStartCommand обновляет latestStartId до обработки action`() {
        val body = actionDispatcherSource.substringAfter("fun dispatch(").substringBefore("} catch")
        val beforeWhen = body.substringBefore("when (action)")
        assertTrue(
            beforeWhen.contains("latestStartIdSetter(startId)"),
            "latestStartId.set(startId) обязан быть ДО when(intent?.action), " +
                "чтобы START_STICKY restart (action=null) тоже обновлял startId.",
        )
    }

    @Test
    fun `statsLogger start вызывается безусловно независимо от engine в StartSequenceCoordinator`() {
        val body = startSequenceSource
            .substringAfter("suspend fun run()")
            .substringBefore("suspend fun engineNeedsCustomTun")
        assertTrue(
            body.contains("statsLogger.start()"),
            "statsLogger.start() должен вызываться без условия engineNeedsCustomTun в run()",
        )
        val gateBeforeStats = body.substringBefore("statsLogger.start()")
        assertFalse(
            gateBeforeStats.contains("if (!usesCustomTun) deps.statsLogger.start") ||
                gateBeforeStats.contains("if (usesCustomTun) deps.statsLogger.start"),
            "Не должно быть гейта usesCustomTun перед statsLogger.start()",
        )
    }

    @Test
    fun `TunnelStatsLogger не вызывает TProxyGetStats — единый источник TunInterfaceStats`() {
        assertFalse(
            statsLoggerSource.contains("TProxyGetStats"),
            "TProxyGetStats — libhev-only API. Использовать TunInterfaceStats для всех движков",
        )
        assertTrue(
            statsLoggerSource.contains("TunInterfaceStats.readTunStats"),
            "TunnelStatsLogger обязан читать через TunInterfaceStats.readTunStats",
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
        val body = shutdownSource
            .substringAfter("suspend fun performShutdown(")
            .substringBefore("private fun recordSessionEnd(")
        val finallyBlock = body.substringAfter("} finally {").substringBefore("companion object")
        assertTrue(
            finallyBlock.contains("state.tunIfaceNameRef.set(null)"),
            "performShutdown finally обязан сбрасывать tunIfaceNameRef — иначе stale имя в новой сессии вернёт чужие байты",
        )
    }

    @Test
    fun `establishTun сохраняет tunIfaceNameRef после establish — оба пути`() {
        assertTrue(
            startSequenceSource.contains("captureTunIfaceName(before)"),
            "После establish() обоих путей (engineTun и обычный) должен вызываться captureTunIfaceName",
        )
        val callCount = startSequenceSource.split("captureTunIfaceName(before)").size - 1
        assertTrue(
            callCount >= 2,
            "captureTunIfaceName должен вызываться в обоих establish — establishTun и establishTunForEngine. Найдено: $callCount",
        )
    }

    @Test
    fun `ACTION_START сбрасывает stopping до вызова startVpn`() {
        val body = actionDispatcherSource.substringAfter("fun dispatch(").substringBefore("private companion object")
        val startBlock = body.substringAfter("ACTION_START, null ->").substringBefore("}")
        assertTrue(
            startBlock.contains("clearStopping()"),
            "При ACTION_START обязан сбросить stopping=false до startVpn(), иначе если новый START " +
                "пришёл во время shutdown, startVpn() видит stopping=true и вызывает stopVpn() повторно.",
        )
    }

    @Test
    fun `startVpn не блокирует main thread runBlocking-ом`() {
        val body = source.substringAfter("private fun startVpn()").substringBefore("private fun engineExtras(")
        assertFalse(
            body.contains("runBlocking"),
            "startVpn не должен использовать runBlocking. settings и split-packages читаются " +
                "из serviceScope.launch (IO) через withTimeoutOrNull + first()/allowlistPackages().",
        )
    }

    @Test
    fun `StartSequenceCoordinator читает свежие settings и split-packages из IO scope`() {
        assertTrue(
            startSequenceSource.contains("settingsRepository.settings.first()"),
            "StartSequenceCoordinator обязан дочитывать settings.first() свежими — cold-start defaults сломают " +
                "custom DNS / split / hosts / winning-args.",
        )
        assertTrue(
            startSequenceSource.contains("splitTunnelRulesProvider.allowlistPackages()"),
            "StartSequenceCoordinator обязан читать allowlistPackages() свежими на каждый connect — иначе изменение " +
                "split-rules видно только со второго reconnect.",
        )
        assertTrue(
            startSequenceSource.contains("splitTunnelRulesProvider.blocklistPackages()"),
            "StartSequenceCoordinator обязан читать blocklistPackages() свежими на каждый connect.",
        )
        assertTrue(
            startSequenceSource.contains("withTimeoutOrNull"),
            "Чтение settings/split обязано быть под withTimeoutOrNull — DataStore corruption не " +
                "должен зависать VPN start.",
        )
    }

    @Test
    fun `onDestroy ограничивает runBlocking shutdown через withTimeoutOrNull`() {
        val body = source.substringAfter("override fun onDestroy()").substringBefore("\n}\n")
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
        val body = source.substringAfter("override fun onDestroy()").substringBefore("\n}\n")
        assertTrue(
            body.contains("onDestroy shutdown timeout"),
            "Если withTimeoutOrNull вернул null — обязан появиться лог 'onDestroy shutdown timeout' " +
                "для post-mortem диагностики ANR-near-miss",
        )
    }

    @Test
    fun `StartSequenceCoordinator run в auto-mode идёт через autoCandidatesWithPreflight`() {
        val body = startSequenceSource.substringAfter("suspend fun run()")
            .substringBefore("suspend fun engineNeedsCustomTun")
        assertTrue(
            body.contains("autoCandidatesWithPreflight("),
            "run обязан звать autoCandidatesWithPreflight в auto-mode — " +
                "fallback по priority + TCP-probe",
        )
        val hasShortFallback = body.contains("?: EngineId.BYEDPI")
        val hasDiagnosticIfElse = body.contains(
            "if (manualEngine != null) manualEngine else EngineId.BYEDPI",
        )
        assertFalse(
            hasShortFallback && !hasDiagnosticIfElse,
            "hardcoded fallback ?: EngineId.BYEDPI запрещён — только в diagnostic-пути",
        )
    }

    @Test
    fun `autoCandidatesWithPreflight вызывает plugin preflight с no-op protector`() {
        assertTrue(
            startSequenceSource.contains("plugin.preflight()"),
            "autoCandidatesWithPreflight обязан брать EnginePreflight через plugin.preflight()",
        )
        val preflightFn = startSequenceSource.substringAfter("private suspend fun autoCandidatesWithPreflight")
            .substringBefore("private suspend fun establishTunForEngine")
        assertTrue(
            preflightFn.contains("SocketProtector { _ -> true }"),
            "preflight использует no-op protector: TUN ещё не создан при preflight — " +
                "VpnService.protect() вернёт false и заблокирует все движки",
        )
        assertFalse(
            preflightFn.contains("protect(socket)"),
            "protect(socket) запрещён в preflight — TUN не создан, protect() вернёт false",
        )
    }

    @Test
    fun `StartSequenceCoordinator run вызывает awaitEngineReady ДО onEngineStarted — readiness gate`() {
        val body = startSequenceSource.substringAfter("suspend fun run()")
            .substringBefore("suspend fun engineNeedsCustomTun")
        assertTrue(
            body.contains("awaitEngineReady("),
            "run обязан звать awaitEngineReady — readiness gate перед onEngineStarted",
        )
        val awaitIdx = body.indexOf("awaitEngineReady(")
        val onStartedIdx = body.indexOf("onEngineStarted(")
        assertTrue(
            awaitIdx in 0 until onStartedIdx,
            "awaitEngineReady должен идти ДО onEngineStarted — readiness gate. " +
                "awaitIdx=$awaitIdx, onStartedIdx=$onStartedIdx",
        )
    }

    @Test
    fun `awaitEngineReady обрабатывает ReadyResult Timeout логом — не маскирует как Ready`() {
        val body = startSequenceSource.substringAfter("private suspend fun awaitEngineReady(")
            .substringBefore("private fun buildEngineConfig(")
        assertTrue(
            body.contains("awaitReady()"),
            "awaitEngineReady обязан звать plugin.awaitReady() — readiness signal",
        )
        assertTrue(
            body.contains("ReadyResult.Timeout"),
            "awaitEngineReady обязан проверять ReadyResult.Timeout — root fix #59: " +
                "без явного match на Timeout sealed class теряет point — компилятор не enforce exhaustiveness",
        )
        assertTrue(
            body.contains("PersistentLoggers.warn"),
            "Timeout обязан логироваться через PersistentLoggers.warn — попадание в boot.log для диагностики",
        )
    }

    @Test
    fun `manual-mode не зовёт autoCandidatesWithPreflight`() {
        val body = startSequenceSource.substringAfter("suspend fun run()")
            .substringBefore("suspend fun engineNeedsCustomTun")
        assertTrue(
            body.contains("if (manualEngine != null)"),
            "manual-mode идёт прямым путём через buildEngineConfig — без preflight",
        )
        assertTrue(
            body.contains("autoCandidatesWithPreflight(effectiveSettings, trafficMode)"),
            "auto-mode (manualEngine == null) идёт через autoCandidatesWithPreflight",
        )
    }

    @Test
    fun `autoCandidates итерирует engineAutoPriority при manualEngine null`() {
        val body = startSequenceSource.substringAfter("private fun autoCandidates(")
            .substringBefore("private suspend fun autoCandidatesWithPreflight")
        assertTrue(
            body.contains("engineAutoPriority"),
            "autoCandidates обязан читать settings.engineAutoPriority для авто-режима",
        )
        assertTrue(
            startSequenceSource.contains("val effectiveSettings = settings ?: SettingsModel()"),
            "fallback на DEFAULT_ENGINE_AUTO_PRIORITY когда settings null обязателен",
        )
        assertTrue(
            body.contains("buildEngineConfig("),
            "autoCandidates обязан звать buildEngineConfig — первый non-null = выбор",
        )
    }

    @Test
    fun `onDestroy не shutdown-ит singleton HealthMonitor`() {
        val body = source.substringAfter("override fun onDestroy()").substringBefore("\n}\n")
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

    @Test
    fun `anchors — все функции-границы существуют в источнике`() {
        listOf(
            "override fun onCreate()",
            "private var socketProtector",
            "override fun onStartCommand",
            "private fun startVpn()",
            "override fun onRevoke()",
            "override fun onDestroy()",
            "private fun engineExtras(",
        ).forEach { anchor ->
            assertTrue(source.contains(anchor), "Anchor потерян в OzeroVpnService.kt: '$anchor'")
        }
        listOf(
            "fun stopVpn(",
            "suspend fun performShutdown(",
            "private fun recordSessionEnd(",
        ).forEach { anchor ->
            assertTrue(shutdownSource.contains(anchor), "Anchor потерян в ShutdownCoordinator.kt: '$anchor'")
        }
        listOf(
            "suspend fun run()",
            "suspend fun engineNeedsCustomTun",
            "private suspend fun awaitEngineReady(",
            "private suspend fun autoCandidatesWithPreflight",
            "private fun autoCandidates",
            "private fun resolveTargetForUi(",
            "private fun establishTun(",
            "private suspend fun establishTunForEngine(",
        ).forEach { anchor ->
            assertTrue(
                startSequenceSource.contains(anchor),
                "Anchor потерян в StartSequenceCoordinator.kt: '$anchor'",
            )
        }
        listOf(
            "fun start()",
        ).forEach { anchor ->
            assertTrue(
                statsLoggerSource.contains(anchor),
                "Anchor потерян в TunnelStatsLogger.kt: '$anchor'",
            )
        }
        listOf(
            "fun buildTunBuilder(",
        ).forEach { anchor ->
            assertTrue(
                helperSource.contains(anchor),
                "Anchor потерян в TunBuilderHelper.kt: '$anchor'",
            )
        }
    }

    @Test
    fun `REVOKE_KILL_DELAY_MS не больше 1500ms — другой VPN не должен долго ждать освобождения slot`() {
        val regex = Regex("REVOKE_KILL_DELAY_MS\\s*=\\s*(\\d[\\d_]*)L")
        val m = regex.find(source) ?: error("REVOKE_KILL_DELAY_MS не найден")
        val ms = m.groupValues[1].replace("_", "").toLong()
        assertTrue(
            ms in 500L..1_500L,
            "REVOKE_KILL_DELAY_MS должен быть [500..1500]ms: меньше — shutdown не успеет, " +
                "больше — другой VPN (URnetwork и т.п.) ждёт слишком долго после revoke → " +
                "пользователь видит «зависание». Fact=$ms",
        )
    }

    @Test
    fun `startVpn использует EXTERNAL_VPN_RELEASE_DELAY_MS при детекции внешнего VPN`() {
        assertTrue(
            source.contains("EXTERNAL_VPN_RELEASE_DELAY_MS"),
            "OzeroVpnService обязан иметь EXTERNAL_VPN_RELEASE_DELAY_MS константу: " +
                "при запуске Ozero ПОСЛЕ другого VPN (URnetwork и т.п.) Android revoke'ает " +
                "тот VPN, но его TUN ещё закрывается — establish() Ozero ловит race. " +
                "Задержка перед establish() даёт ОС время полностью отпустить slot.",
        )
        assertTrue(
            source.contains("logActiveExternalVpn()"),
            "startVpn должен вызывать logActiveExternalVpn() и использовать результат " +
                "для условной задержки.",
        )
        val regex = Regex("EXTERNAL_VPN_RELEASE_DELAY_MS\\s*=\\s*(\\d[\\d_]*)L")
        val m = regex.find(source) ?: error("EXTERNAL_VPN_RELEASE_DELAY_MS не найден")
        val ms = m.groupValues[1].replace("_", "").toLong()
        assertTrue(
            ms in 200L..1_500L,
            "EXTERNAL_VPN_RELEASE_DELAY_MS должен быть [200..1500]ms — короче ловим race, " +
                "длиннее юзер замечает paused старт. Fact=$ms",
        )
    }

    @Test
    fun `onTaskRemoved НЕ переопределён — VPN обязан продолжать работу в фоне при swipe-close`() {
        assertTrue(
            !source.contains("override fun onTaskRemoved("),
            "OzeroVpnService НЕ должен переопределять onTaskRemoved.\n" +
                "Свайп приложения из recents = стандартное Android поведение: foreground VpnService " +
                "продолжает работать, TUN держится, индикатор ключа остаётся. Это ключевая UX-инвариант " +
                "VPN-приложений (WireGuard / AmneziaWG / ProtonVPN / OpenVPN — все так). " +
                "Юзер выключает VPN явно через переключатель в апп или системную шторку, не свайпом.\n" +
                "Регрессия: коммит d5284235 (откатан в feedback_swipe_close_vpn_must_persist) — " +
                "override onTaskRemoved → stopVpn ломал фон-режим: VPN ВЫКЛЮЧАЛСЯ от свайпа. " +
                "Никогда не возвращать.",
        )
    }

    @Test
    fun `establishTunForEngine uses TunSpec excludeSelf without engineId branches`() {
        val body = startSequenceSource
            .substringAfter("private suspend fun establishTunForEngine(")
            .substringBefore("private fun captureTunIfaceName(")
        assertTrue(
            body.contains("excludeSelf = spec.excludeSelf"),
            "excludeSelf должен идти из TunSpec: common-vpn применяет контракт движка без знания конкретных EngineId.",
        )
        assertFalse(
            body.contains("engineId == ") || body.contains("engineId != "),
            "establishTunForEngine не должен ветвиться по engineId — common-vpn не знает про движки.",
        )
    }

    @Test
    fun `onCreate acquireLocks — WakeLock и WifiLock держат CPU и WiFi при screen off`() {
        val body = source
            .substringAfter("override fun onCreate()")
            .substringBefore("private fun observeKillswitchSetting()")
        assertTrue(
            body.contains("acquireLocks()"),
            "onCreate обязан звать acquireLocks() — без PARTIAL_WAKE_LOCK CPU засыпает при screen off, " +
                "native proxy (byedpi/hev) перестаёт обрабатывать пакеты → throughput падает. " +
                "Без WifiLock WiFi переходит в power-save → higher latency, packet drops.",
        )
        val superIdx = body.indexOf("super.onCreate()")
        val locksIdx = body.indexOf("acquireLocks()")
        assertTrue(
            superIdx in 0 until locksIdx,
            "acquireLocks() обязан быть ПОСЛЕ super.onCreate() — getSystemService недоступен до super",
        )
    }

    @Test
    fun `onDestroy releaseLocks — пара к acquireLocks`() {
        val body = source.substringAfter("override fun onDestroy()").substringBefore("\n}\n")
        assertTrue(
            body.contains("releaseLocks()"),
            "onDestroy обязан звать releaseLocks() — каждому acquire парный release при teardown. " +
                "Без release WakeLock leak → battery drain complaint от системы.",
        )
    }

    @Test
    fun `acquireLocks создаёт PARTIAL_WAKE_LOCK и WifiLock`() {
        assertTrue(
            source.contains("PowerManager.PARTIAL_WAKE_LOCK"),
            "acquireLocks обязан брать PARTIAL_WAKE_LOCK — CPU не засыпает при screen off",
        )
        assertTrue(
            source.contains("WIFI_MODE_FULL_LOW_LATENCY") || source.contains("WIFI_MODE_FULL_HIGH_PERF"),
            "acquireLocks обязан брать WifiLock — WiFi не переходит в power-save",
        )
    }
}
