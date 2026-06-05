package ru.ozero.engineurnetwork

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertTrue

@Suppress("LargeClass")
class RealUrnetworkSdkBridgeContractTest {

    private val source by lazy {
        val moduleRoot = File(System.getProperty("user.dir") ?: ".")
        val f = File(moduleRoot, "src/main/java/ru/ozero/engineurnetwork/RealUrnetworkSdkBridge.kt")
        assertTrue(f.exists(), "RealUrnetworkSdkBridge.kt не найден: $f")
        f.readText()
    }

    @Test
    fun `start() guard already running возвращает Success — идемпотентен для RelayCoordinator`() {
        val guardBlock = source.substringAfter("if (running.get())").substringBefore("startJobRef.set")
        assertTrue(
            source.contains("running.get()"),
            "start() обязан проверять running.get() при двойном вызове",
        )
        assertTrue(
            guardBlock.contains("StartResult.Success"),
            "start() при already running обязан возвращать Success (идемпотентность для RelayCoordinator) — " +
                "возврат Failed блокирует relay когда URnetwork-движок уже запущен bridge.",
        )
        assertTrue(
            !guardBlock.contains("StartResult.Failed"),
            "start() при already running НЕ должен возвращать Failed — relay coordinator не сможет запустить relay.",
        )
    }

    @Test
    fun `stop() сбрасывает running и cancel'ит in-flight start ДО lifecycleMutex withLock`() {
        val stopBlock = source.substringAfter("override suspend fun stop() {")
            .substringBefore("private suspend fun stopUnderLock")
        val runningSetFalseIdx = stopBlock.indexOf("running.set(false)")
        val startCancelIdx = stopBlock.indexOf("startJobRef.getAndSet(null)")
        val withLockIdx = stopBlock.indexOf("lifecycleMutex.withLock")
        assertTrue(
            runningSetFalseIdx in 0 until withLockIdx,
            "running.set(false) обязан быть ДО lifecycleMutex.withLock — иначе stop ждёт мьютекс " +
                "за 30s start init, а JNI gates остаются открытыми.",
        )
        assertTrue(
            startCancelIdx in 0 until withLockIdx,
            "startJobRef.getAndSet(null) с cancel обязан быть ДО lifecycleMutex.withLock — иначе " +
                "stop тщетно ждёт mutex который держит in-flight start. Симптом: 'already running' " +
                "на следующий start после неудачной попытки переключения движка.",
        )
    }

    @Test
    fun `start() регистрирует свой Job в startJobRef для stop()-cancel`() {
        val startBlock = source.substringAfter("override suspend fun start(")
            .substringBefore("private suspend fun runStartOnMain")
        assertTrue(
            startBlock.contains("startJobRef.set"),
            "start() обязан зарегистрировать свой Job в startJobRef — иначе stop() не сможет " +
                "cancel'ить in-flight init для освобождения lifecycleMutex.",
        )
        assertTrue(
            startBlock.contains("startJobRef.compareAndSet"),
            "start() обязан очищать startJobRef в finally — иначе stale Job-ref после успешного start.",
        )
    }

    @Test
    fun `start() требует non-blank byClientJwt`() {
        assertTrue(source.contains("byClientJwt.isBlank()") && source.contains("byClientJwt is blank"))
    }

    @Test
    fun `start() и attachTun выполняются на Main thread`() {
        assertTrue(
            source.contains("Dispatchers.Main.immediate"),
            "Go runtime + non-locked OSThread = SIGABRT на Nubia/RedMagic",
        )
    }

    @Test
    fun `applyDeviceFields helper применяет 13 device-полей — единый источник правды`() {
        val block = source.substringAfter("private fun applyDeviceFields(")
            .substringBefore("private inline fun guardedRun")
        val requiredFields = listOf(
            "providePaused",
            "routeLocal",
            "provideMode",
            "connectLocation",
            "defaultLocation",
            "canShowRatingDialog",
            "provideControlMode",
            "vpnInterfaceWhileOffline",
            "canRefer",
            "allowForeground",
            "provideNetworkMode",
            "canPromptIntroFunnel",
            "performanceProfile",
        )
        requiredFields.forEach { field ->
            assertTrue(
                block.contains("device.$field ="),
                "applyDeviceFields обязан выставлять device.$field — " +
                    "паритет с upstream DeviceManager.kt:132-144. Без этого SDK возвращает только " +
                    "страны без городов/регионов в LocationsViewController.",
            )
        }
    }

    @Test
    fun `applyDeviceFields делает default best available явным connectLocation`() {
        val block = source.substringAfter("private fun applyDeviceFields(")
            .substringBefore("private inline fun guardedRun")
        assertTrue(
            block.contains("bestAvailableConnectLocation()"),
            "applyDeviceFields не должен оставлять device.connectLocation null для default Best Available — " +
                "иначе SDK стартует без выбранной connect location до ручного клика.",
        )
        assertTrue(
            source.contains("ConnectLocationId") && source.contains("id.bestAvailable = true"),
            "Best Available должен передаваться в SDK как ConnectLocationId.bestAvailable=true.",
        )
    }

    @Test
    fun `runStartOnMain и ensureDeviceOnMain делегируют в applyDeviceFields — single source of truth`() {
        val startBlock = source.substringAfter("private suspend fun runStartOnMain")
            .substringBefore("private suspend fun stop")
        val ensureBlock = source.substringAfter("private suspend fun ensureDeviceOnMain")
            .substringBefore("private fun applyDeviceFields")
        assertTrue(
            startBlock.contains("applyDeviceFields(d, localState)"),
            "runStartOnMain обязан делегировать применение 13 device-полей в applyDeviceFields — " +
                "иначе разъезжается с ensureDeviceOnMain (как в v0.1.7) → SDK скрывает regions/cities " +
                "при engine.start без открытия settings.",
        )
        assertTrue(
            ensureBlock.contains("applyDeviceFields(device, localState)"),
            "ensureDeviceOnMain обязан делегировать в applyDeviceFields — " +
                "иначе разъезжается с runStartOnMain.",
        )
    }

    @Test
    fun `applyDeviceFields override на ProvideModePublic при ALWAYS — root cause regions cities скрытых`() {
        val block = source.substringAfter("private fun applyDeviceFields(")
            .substringBefore("private inline fun guardedRun")
        assertTrue(
            block.contains("Sdk.ProvideModePublic"),
            "applyDeviceFields обязан override provideMode на Sdk.ProvideModePublic когда " +
                "provideControlMode == ALWAYS — паритет с upstream DeviceManager.kt:105. " +
                "Без override свежий юзер имеет localState.provideMode = ProvideModeNone (0), " +
                "и SDK скрывает regions/cities в LocationsViewController (только countries).",
        )
        assertTrue(
            block.contains("UrnetworkProvideControlMode.fromRaw"),
            "applyDeviceFields обязан нормализовать provideControlMode через fromRaw — " +
                "raw localState.provideControlMode может быть '' или невалидный, SDK тогда " +
                "не активирует location hierarchy.",
        )
        assertTrue(
            block.contains("UrnetworkProvideControlMode.ALWAYS.rawValue"),
            "Override branch обязан сравнивать с UrnetworkProvideControlMode.ALWAYS.rawValue — " +
                "иначе магическая строка \"always\" разъедется с enum при rename.",
        )
    }

    @Test
    fun `bridge использует UrnetworkRuntime ensure (один manager на процесс)`() {
        assertTrue(
            source.contains("UrnetworkRuntime.ensure(app)"),
            "Bridge обязан использовать singleton Runtime — два NetworkSpaceManager = SIGABRT",
        )
        assertTrue(
            !source.contains("Sdk.newNetworkSpaceManager"),
            "Bridge НЕ должен создавать свой manager",
        )
    }

    @Test
    fun `Application context not Activity`() {
        assertTrue(source.contains("private val app: Application"))
    }

    @Test
    fun `attachTun валидирует fd и device state и double-attach`() {
        val attachBlock = source.substringAfter("override suspend fun attachTun")
            .substringBefore("private fun cleanupOnFailure")
        assertTrue(attachBlock.contains("tunFd < 0") && attachBlock.contains("invalid fd"))
        assertTrue(attachBlock.contains("DeviceLocal not initialised"))
        assertTrue(attachBlock.contains("IoLoop already attached"))
        assertTrue(attachBlock.contains("catch (t: Throwable)") && attachBlock.contains("newIoLoop failed"))
    }

    @Test
    fun `attachTun setTunnelStarted true ПОСЛЕ newIoLoop`() {
        val attachBlock = source.substringAfter("override suspend fun attachTun")
            .substringBefore("private fun cleanupOnFailure")
        val ioLoopIdx = attachBlock.indexOf("Sdk.newIoLoop")
        val tunnelStartedIdx = attachBlock.indexOf("setTunnelStarted(true)")
        assertTrue(ioLoopIdx >= 0 && tunnelStartedIdx >= 0)
        assertTrue(tunnelStartedIdx > ioLoopIdx)
    }

    @Test
    fun `start() не вызывает setTunnelStarted true`() {
        val startBlock = source.substringAfter("private suspend fun runStartOnMain")
            .substringBefore("override suspend fun stop")
        assertTrue(!startBlock.contains("setTunnelStarted(true)"))
    }

    @Test
    fun `attachTun регистрирует IoLoopDoneCallback который сбрасывает running через compareAndSet`() {
        val attachBlock = source.substringAfter("override suspend fun attachTun")
            .substringBefore("private fun cleanupOnFailure")
        assertTrue(attachBlock.contains("IoLoopDoneCallback"))
        assertTrue(
            attachBlock.contains("running.compareAndSet(true, false)"),
            "IoLoopDoneCallback обязан использовать compareAndSet чтобы отличить graceful stop " +
                "(running уже false) от crash (running всё ещё true). Без этого нельзя различить " +
                "ожидаемое завершение и SDK runtime crash (P32 audit).",
        )
    }

    @Test
    fun `onIoLoopDied callback присутствует в конструкторе`() {
        assertTrue(
            source.contains("private val onIoLoopDied: (String) -> Unit"),
            "RealUrnetworkSdkBridge обязан принимать onIoLoopDied callback — иначе TunnelController " +
                "не узнаёт о crash SDK runtime до тех пор пока peer watchdog не сработает (30s leak window).",
        )
    }

    @Test
    fun `IoLoopDoneCallback вызывает onIoLoopDied только при wasRunning`() {
        val attachBlock = source.substringAfter("override suspend fun attachTun")
            .substringBefore("private fun cleanupOnFailure")
        val callbackBody = attachBlock.substringAfter("IoLoopDoneCallback {").substringBefore("}\n")
        assertTrue(
            callbackBody.contains("if (wasRunning)"),
            "IoLoopDoneCallback обязан вызывать onIoLoopDied ТОЛЬКО если compareAndSet вернул true — " +
                "иначе graceful stop (running уже false) ложно триггерит killswitch. Body=$callbackBody",
        )
        assertTrue(
            callbackBody.contains("onIoLoopDied("),
            "Callback обязан вызвать onIoLoopDied при detected crash. Body=$callbackBody",
        )
    }

    @Test
    fun `openConnectViewController вызывается в start а не в attachTun`() {
        val startBlock = source.substringAfter("private suspend fun runStartOnMain")
            .substringBefore("override suspend fun stop")
        val attachBlock = source.substringAfter("override suspend fun attachTun")
            .substringBefore("private fun cleanupOnFailure")
        assertTrue(
            startBlock.contains("openConnectViewController"),
            "ConnectViewController открывается в start() — нужен для location picker до подключения",
        )
        assertTrue(
            !attachBlock.contains("openConnectViewController"),
            "attachTun не должен открывать ConnectViewController — он уже открыт в start()",
        )
    }

    @Test
    fun `runStartOnMain возвращает Failed если openConnectViewController вернул null — guard infinite peer search`() {
        val startBlock = source.substringAfter("private suspend fun runStartOnMain")
            .substringBefore("override suspend fun stop")
        val afterCvCheck = startBlock.substringAfter("openConnectViewController")
        assertTrue(
            afterCvCheck.contains("StartResult.Failed"),
            "runStartOnMain обязан возвращать StartResult.Failed если openConnectViewController null — " +
                "без этого running=true с null connectVc → peerCount() всегда 0 → бесконечный peer search",
        )
        assertTrue(
            afterCvCheck.contains("cleanupOnFailure"),
            "runStartOnMain обязан вызвать cleanupOnFailure() при null connectVc — иначе deviceRef утекает",
        )
    }

    @Test
    fun `attachTun вызывает connectBestAvailable через существующий connectVcRef`() {
        val attachBlock = source.substringAfter("override suspend fun attachTun")
            .substringBefore("private fun cleanupOnFailure")
        assertTrue(
            attachBlock.contains("connectBestAvailable"),
            "P2P соединение не установится без connectBestAvailable в attachTun",
        )
        assertTrue(
            attachBlock.contains("connectVcRef.get()"),
            "connectBestAvailable должен вызываться через connectVcRef, не через новый объект",
        )
    }

    @Test
    fun `connect methods persist selected location only after successful connect`() {
        val connectToBlock = source.substringAfter("override fun connectTo(")
            .substringBefore("override fun connectBestAvailable()")
        assertTrue(
            connectToBlock.contains("runCatching { cv.connect(sdkLoc) }"),
            "connectTo must call connect before persisting location.",
        )
        assertTrue(
            connectToBlock.contains(".onSuccess {"),
            "connectTo must persist only on successful connect.",
        )
        assertTrue(
            connectToBlock.indexOf("cv.connect(sdkLoc)") < connectToBlock.indexOf("persistConnectLocation(sdkLoc)"),
            "connectTo must persist after connect succeeds.",
        )

        val bestBlock = source.substringAfter("override fun connectBestAvailable()")
            .substringBefore("override fun selectedLocation()")
        assertTrue(
            bestBlock.contains("runCatching { cv.connectBestAvailable() }"),
            "connectBestAvailable must call connect before persisting location.",
        )
        assertTrue(
            bestBlock.contains(".onSuccess {"),
            "connectBestAvailable must persist only on successful connect.",
        )
        val connectIndex = bestBlock.indexOf("cv.connectBestAvailable()")
        val persistIndex = bestBlock.indexOf("persistConnectLocation(bestAvailableConnectLocation())")
        assertTrue(connectIndex < persistIndex, "connectBestAvailable must persist after connect succeeds.")
    }

    @Test
    fun `stop() не закрывает device пока IoLoop активен — SIGABRT guard`() {
        val stopBlock = source.substringAfter("override suspend fun stop").substringBefore("override fun isRunning")
        assertTrue(
            stopBlock.contains("hadLoop") || stopBlock.contains("IoLoop still running"),
            "device.close() в stop() при активном IoLoop = use-after-free → SIGABRT на Nubia/RedMagic",
        )
    }

    @Test
    fun `IoLoopDoneCallback закрывает device после завершения IoLoop`() {
        val attachBlock = source.substringAfter("override suspend fun attachTun")
            .substringBefore("private fun cleanupOnFailure")
        assertTrue(
            attachBlock.contains("closeDevice"),
            "device должен закрываться в IoLoopDoneCallback, не в stop()",
        )
    }

    @Test
    fun `stop() освобождает connectVcRef с disconnect и close`() {
        val stopBlock = source.substringAfter("override suspend fun stop").substringBefore("override fun isRunning")
        assertTrue(stopBlock.contains("connectVcRef.getAndSet(null)"))
        assertTrue(stopBlock.contains("vc.disconnect()"))
        assertTrue(stopBlock.contains("vc.close()"))
    }

    @Test
    fun `stop() закрывает walletVcRef — SDK освобождает Sub listeners при close VC`() {
        val stopBlock = source.substringAfter("override suspend fun stop").substringBefore("override fun isRunning")
        assertTrue(
            stopBlock.contains("walletVcRef.getAndSet(null)"),
            "stopUnderLock обязан забирать walletVcRef.getAndSet(null) — иначе sticky ref + " +
                "addUnpaidByteCountListener Sub утекает до конца процесса. " +
                "Pattern: walletVc.close() освобождает Sub'ы (upstream WalletViewModel.kt:522-526).",
        )
    }

    @Test
    fun `stop() очищает ioLoop через runCatching`() {
        val stopBlock = source.substringAfter("override suspend fun stop").substringBefore("override fun isRunning")
        assertTrue(stopBlock.contains("running.set(false)"))
        assertTrue(stopBlock.contains("ioLoopRef.getAndSet(null)"))
        val runCatchingCount = stopBlock.split("runCatching").size - 1
        assertTrue(runCatchingCount >= 2, "Каждый close() в runCatching, found=$runCatchingCount")
    }

    @Test
    fun `все JNI-методы гейтятся через running get() — SIGABRT guard на UI poll vs teardown race`() {
        val gatedMethods = listOf(
            "connectTo", "connectBestAvailable",
            "selectedLocation", "selectedLocationInfo",
            "openLocationsViewController",
            "setProvidePaused", "isProvidePaused",
            "applyPerformanceProfile",
            "peerCount", "fetchTransferStats", "fetchSubscriptionBalance",
        )
        for (method in gatedMethods) {
            val signature = "override (?:suspend )?fun $method"
            val regex = Regex(signature)
            val match = regex.find(source) ?: error("Не найден метод $method")
            val body = source.substring(match.range.last + 1).take(400)
            assertTrue(
                body.contains("running.get()") || body.contains("!running.get()"),
                "Метод $method обязан проверять running.get() ДО любого JNI вызова. " +
                    "Race window: UI VM polls bridge.$method во время engine teardown → " +
                    "JNI входит в Go-объект который тушит другой поток → SIGABRT в gcWriteBarrier. " +
                    "Body=${body.take(300)}",
            )
        }
    }

    @Test
    fun `attachTun сериализуется через lifecycleMutex — guard на race со stop`() {
        val attachOuterBlock = source.substringAfter("override suspend fun attachTun")
            .substringBefore("private suspend fun attachTunUnderLock")
        assertTrue(
            attachOuterBlock.contains("lifecycleMutex.withLock"),
            "attachTun обязан брать lifecycleMutex.withLock перед делегацией в attachTunUnderLock — " +
                "иначе concurrent stop() закрывает device/connectVc/ioLoop пока attachTun дёргает Sdk.newIoLoop. " +
                "Race window: stopUnderLock освобождает refs → attachTunUnderLock ставит ioLoopRef поверх — " +
                "висячий loop без device → SIGABRT в Go-runtime на следующий poll.",
        )
        assertTrue(
            attachOuterBlock.contains("attachJobRef.set(myJob)"),
            "attachTun обязан регистрировать свой Job в attachJobRef — иначе stop() не сможет cancel'ить " +
                "in-flight attachTun для освобождения lifecycleMutex.",
        )
        assertTrue(
            attachOuterBlock.contains("attachJobRef.compareAndSet(myJob, null)"),
            "attachTun обязан очищать attachJobRef в finally — иначе stale Job-ref после успешного attachTun.",
        )
    }

    @Test
    fun `stop отменяет in-flight attachTun ДО lifecycleMutex withLock`() {
        val stopBlock = source.substringAfter("override suspend fun stop()")
            .substringBefore("private suspend fun stopUnderLock")
        val attachCancelIdx = stopBlock.indexOf("attachJobRef.getAndSet(null)")
        val withLockIdx = stopBlock.indexOf("lifecycleMutex.withLock")
        assertTrue(
            attachCancelIdx in 0 until withLockIdx,
            "attachJobRef.getAndSet(null) с cancel обязан быть ДО lifecycleMutex.withLock — иначе stop " +
                "висит за timeout пока attachTun держит mutex с blocking Sdk.newIoLoop. " +
                "Симптом: stop timed out after Xms — refs cleared, зомби IoLoop остался жив.",
        )
    }

    @Test
    fun `start и stop сериализуются через lifecycleMutex`() {
        assertTrue(
            source.contains("private val lifecycleMutex = Mutex()") &&
                source.contains("lifecycleMutex.withLock"),
            "start/stop обязан проходить через lifecycleMutex. Concurrent start+stop = " +
                "race на deviceRef/ioLoopRef → потерянные refs → Go-runtime leak → SIGABRT на следующий init.",
        )
    }

    @Test
    fun `payoutWalletSetup configure вызывается после walletVc start с walletAddress`() {
        val startBlock = source.substringAfter("private suspend fun runStartOnMain")
            .substringBefore("override suspend fun stop")
        assertTrue(
            startBlock.contains("payoutWalletSetup.configure("),
            "runStartOnMain обязан вызвать payoutWalletSetup.configure(...) после walletVc.start()",
        )
        assertTrue(
            source.contains("private val payoutWalletSetup = UrnetworkPayoutWalletSetup()"),
            "Bridge обязан держать UrnetworkPayoutWalletSetup helper — иначе single-source-of-truth " +
                "для payout wallet setup ломается (детали в UrnetworkPayoutWalletSetup.kt).",
        )
    }

    @Test
    fun `setPreferredLocation сохраняет UrnetworkLocationSelection через normalized`() {
        assertTrue(
            source.contains("override fun setPreferredLocation(selection: UrnetworkLocationSelection?)"),
            "Bridge обязан принимать UrnetworkLocationSelection (country+region+city), не код страны — " +
                "иначе region/city не передаются в SDK.",
        )
        val body = source.substringAfter("override fun setPreferredLocation(selection: UrnetworkLocationSelection?)")
            .substringBefore("override fun openLocationsViewController")
        assertTrue(
            body.contains("preferredLocationRef.set"),
            "setPreferredLocation обязан сохранять selection в preferredLocationRef для использования " +
                "в connectByPreferredLocation после attachTun.",
        )
        assertTrue(
            body.contains("normalized()"),
            "setPreferredLocation обязан вызывать selection.normalized() — иначе пустые/невалидные " +
                "country codes попадут в connect matching и сорвут поиск.",
        )
    }

    @Test
    fun `preferredLocationConnector connect вызывается в attachTun под running guard`() {
        val helperField =
            "private val preferredLocationConnector = UrnetworkPreferredLocationConnector(bridgeScope)"
        assertTrue(
            source.contains(helperField),
            "Bridge обязан держать UrnetworkPreferredLocationConnector helper — иначе single-source-of-truth " +
                "для preferred-location matcher ломается (детали в UrnetworkPreferredLocationConnector.kt).",
        )
        assertTrue(
            source.contains("preferredLocationConnector.connect("),
            "Bridge обязан вызывать preferredLocationConnector.connect(...) для user-selected region/city " +
                "после attachTun — иначе hierarchical matcher не активируется.",
        )
    }

    @Test
    fun `contractStatusListener attach в start path и detach в stopUnderLock`() {
        assertTrue(
            source.contains("private val contractStatusListener = UrnetworkContractStatusListener()"),
            "Bridge обязан держать UrnetworkContractStatusListener — иначе contractStatus flow всегда UNKNOWN, " +
                "UrnetworkContractStatusObserver не сможет ловить insufficient balance.",
        )
        val startBlock = source.substringAfter("private suspend fun runStartOnMain")
            .substringBefore("override suspend fun stop")
        assertTrue(
            startBlock.contains("contractStatusListener.attach("),
            "runStartOnMain обязан вызывать contractStatusListener.attach(device) после device init — " +
                "иначе listener никогда не подпишется и insufficientBalance событие не дойдёт до Observer.",
        )
        val stopBlock = source.substringAfter("private suspend fun stopUnderLock")
            .substringBefore("private fun cleanupOnFailure")
        assertTrue(
            stopBlock.contains("contractStatusListener.detach()"),
            "stopUnderLock обязан вызывать contractStatusListener.detach() — иначе listener " +
                "remains attached → memory leak + ghost callbacks после teardown.",
        )
    }

    @Test
    fun `contractStatus override возвращает contractStatusListener_status`() {
        val regex = Regex(
            "override fun contractStatus\\(\\)[^=]*=\\s*contractStatusListener\\.status",
            RegexOption.DOT_MATCHES_ALL,
        )
        assertTrue(
            regex.containsMatchIn(source),
            "contractStatus() обязан возвращать contractStatusListener.status — отдельный StateFlow создаст " +
                "разлив truth: UI читает один flow, listener эмитит в другой, signal теряется.",
        )
    }

    @Test
    fun `connectionStatus listener attached in start and detached in stop`() {
        val startBlock = source.substringAfter("private suspend fun runStartOnMain")
            .substringBefore("override suspend fun stop")
        assertTrue(
            source.contains("private val connectionStatusRef = AtomicReference<String?>"),
            "Bridge must keep ConnectViewController.connectionStatus separately from grid.windowCurrentSize.",
        )
        assertTrue(
            startBlock.contains("attachConnectionStatusListener(cv)"),
            "runStartOnMain must subscribe to addConnectionStatusListener after openConnectViewController.",
        )
        val listenerBlock = source.substringAfter("private fun attachConnectionStatusListener")
            .substringBefore("private fun detachConnectionStatusListener")
        assertTrue(
            listenerBlock.contains("bridgeScope.launch(Dispatchers.Main.immediate)"),
            "connectionStatus listener must marshal callback work onto the main thread.",
        )
        val stopBlock = source.substringAfter("private suspend fun stopUnderLock")
            .substringBefore("private fun cleanupOnFailure")
        assertTrue(
            stopBlock.contains("detachConnectionStatusListener()"),
            "stopUnderLock must close connectionStatus Sub to prevent callbacks after teardown.",
        )
    }

    @Test
    fun `selectedLocation listener persists SDK chosen country after Best Available connect`() {
        val startBlock = source.substringAfter("private suspend fun runStartOnMain")
            .substringBefore("override suspend fun stop")
        assertTrue(
            source.contains("private val selectedLocationSubRef = AtomicReference<Sub?>(null)"),
            "Bridge must keep SelectedLocation Sub so SDK-chosen Best Available country is persisted.",
        )
        assertTrue(
            startBlock.contains("attachSelectedLocationListener(cv)"),
            "runStartOnMain must subscribe to selectedLocation changes after openConnectViewController.",
        )
        val listenerBlock = source.substringAfter("private fun attachSelectedLocationListener")
            .substringBefore("private fun refreshConnectionStatus")
        assertTrue(
            listenerBlock.contains("bridgeScope.launch(Dispatchers.Main.immediate)"),
            "selectedLocation listener must marshal SDK callback work onto the main thread.",
        )
        assertTrue(
            listenerBlock.contains("addSelectedLocationListener") &&
                listenerBlock.contains("persistConnectLocation(location)"),
            "selectedLocation listener must persist actual SDK location, otherwise settings UI only sees <best>.",
        )
        val stopBlock = source.substringAfter("private suspend fun stopUnderLock")
            .substringBefore("private fun cleanupOnFailure")
        assertTrue(
            stopBlock.contains("detachSelectedLocationListener()"),
            "stopUnderLock must close selectedLocation Sub to prevent callbacks after teardown.",
        )
    }

    @Test
    fun `runtimeSnapshot exposes consumer readiness without relay state`() {
        val body = source.substringAfter("override fun runtimeSnapshot()")
            .substringBefore("override fun openLocationsViewController")
        assertTrue(
            body.contains("windowStatus") && body.contains("providerStateAdded"),
            "runtimeSnapshot must expose SDK windowStatus.providerStateAdded as consumer readiness signal.",
        )
        assertTrue(
            body.contains("tunnelStarted") && body.contains("connectIssued"),
            "runtimeSnapshot must separate attach/connect readiness from peer count.",
        )
        assertTrue(
            !body.contains("provideMode") && !body.contains("unpaidBytes"),
            "runtimeSnapshot is for URnetwork consumer engine and must not couple to relay payout/provide state.",
        )
    }

    @Test
    fun `cleanupOnFailure closes device without throw sentinel`() {
        val cleanupBlock = source.substringAfter("private fun cleanupOnFailure")
            .substringBefore("private companion object")
        assertTrue(cleanupBlock.contains("deviceRef.getAndSet(null)"))
        assertTrue(cleanupBlock.contains("runCatching"))
    }

    @Test
    fun `runStartOnMain addProvideSecretKeysListener регистрируется до initProvideSecretKeys sentinel`() {
        val startBlock = source.substringAfter("private suspend fun runStartOnMain")
            .substringBefore("override suspend fun stop")
        val listenerIdx = startBlock.indexOf("addProvideSecretKeysListener")
        val initIdx = startBlock.indexOf("d.initProvideSecretKeys()")
        assertTrue(
            listenerIdx >= 0,
            "runStartOnMain обязан вызывать addProvideSecretKeysListener — без listener'а generated keys " +
                "никогда не сохраняются в localState, device становится новым provider identity на каждом рестарте → 0 раздачи.",
        )
        assertTrue(
            listenerIdx < initIdx,
            "addProvideSecretKeysListener обязан регистрироваться ДО d.initProvideSecretKeys() — " +
                "иначе callback может сработать до регистрации listener'а и ключи потеряются.",
        )
    }

    @Test
    fun `runStartOnMain listener сохраняет сгенерированные ключи в localState`() {
        val startBlock = source.substringAfter("private suspend fun runStartOnMain")
            .substringBefore("override suspend fun stop")
        val listenerBody = startBlock.substringAfter("addProvideSecretKeysListener {")
            .substringBefore("d.initProvideSecretKeys()")
        assertTrue(
            listenerBody.contains("localState.provideSecretKeys = "),
            "Listener в runStartOnMain обязан сохранять ключи через localState.provideSecretKeys — " +
                "без этого provider identity сбрасывается на каждом рестарте → 0 раздачи.",
        )
    }

    @Test
    fun `ensureDeviceOnMain addProvideSecretKeysListener регистрируется до initProvideSecretKeys sentinel`() {
        val ensureBlock = source.substringAfter("private suspend fun ensureDeviceOnMain")
            .substringBefore("private fun applyDeviceFields")
        val listenerIdx = ensureBlock.indexOf("addProvideSecretKeysListener")
        val initIdx = ensureBlock.indexOf("device.initProvideSecretKeys()")
        assertTrue(
            listenerIdx >= 0,
            "ensureDeviceOnMain обязан вызывать addProvideSecretKeysListener — " +
                "этот path используется для location browse без full engine start (settings screen).",
        )
        assertTrue(
            listenerIdx < initIdx,
            "addProvideSecretKeysListener обязан регистрироваться ДО device.initProvideSecretKeys().",
        )
    }

    @Test
    fun `runStartOnMain регистрирует addJwtRefreshListener — JWT auto-refresh обновляет localState`() {
        val startBlock = source.substringAfter("private suspend fun runStartOnMain")
            .substringBefore("override suspend fun stop")
        assertTrue(
            startBlock.contains("addJwtRefreshListener"),
            "runStartOnMain обязан вызывать addJwtRefreshListener — без него JWT-refresh SDK не обновляет " +
                "localState.byClientJwt, следующий bridge.start перезапишет refreshed token старым из configStore.",
        )
        val listenerBody = startBlock.substringAfter("addJwtRefreshListener {")
            .substringBefore("applyDeviceFields")
        assertTrue(
            listenerBody.contains("bridgeScope.launch(Dispatchers.Main.immediate)"),
            "addJwtRefreshListener callback must marshal LocalState mutation onto the main thread.",
        )
        assertTrue(
            listenerBody.contains("localState.byClientJwt = "),
            "addJwtRefreshListener callback обязан сохранять newJwt в localState.byClientJwt — " +
                "иначе SDK RotationJWT не персистится через lifecycle bridge.stop()/start().",
        )
    }

    @Test
    fun `ensureDeviceOnMain addJwtRefreshListener присутствует — reuse device получает JWT listener`() {
        val ensureBlock = source.substringAfter("private suspend fun ensureDeviceOnMain")
            .substringBefore("private fun applyDeviceFields")
        assertTrue(
            ensureBlock.contains("addJwtRefreshListener"),
            "ensureDeviceOnMain обязан вызывать addJwtRefreshListener — если runStartOnMain переиспользует " +
                "этот device, JWT refresh listener иначе никогда не будет добавлен к нему.",
        )
        val listenerBody = ensureBlock.substringAfter("addJwtRefreshListener {")
            .substringBefore("applyDeviceFields")
        assertTrue(
            listenerBody.contains("bridgeScope.launch(Dispatchers.Main.immediate)"),
            "ensureDevice addJwtRefreshListener callback must marshal LocalState mutation onto the main thread.",
        )
        assertTrue(
            listenerBody.contains("localState.byClientJwt = "),
            "ensureDevice addJwtRefreshListener callback обязан сохранять newJwt в localState.byClientJwt.",
        )
    }

    @Test
    fun `provideSecretKeys listener вызывает sub close после сохранения — one-shot listener`() {
        val startBlock = source.substringAfter("private suspend fun runStartOnMain")
            .substringBefore("override suspend fun stop")
        val listenerBody = startBlock.substringAfter("addProvideSecretKeysListener {")
            .substringBefore("d.initProvideSecretKeys()")
        assertTrue(
            listenerBody.contains("sub?.close()"),
            "Listener обязан закрывать Sub после первого вызова — иначе listener остаётся attached " +
                "и может перезаписывать localState.provideSecretKeys при будущих regeneration.",
        )
    }
}
