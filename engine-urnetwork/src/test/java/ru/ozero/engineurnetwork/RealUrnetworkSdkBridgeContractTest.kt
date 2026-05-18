package ru.ozero.engineurnetwork

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertTrue

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
    fun `setupPayoutWallet вызывается после walletVc start с walletAddress`() {
        val startBlock = source.substringAfter("private suspend fun runStartOnMain")
            .substringBefore("override suspend fun stop")
        assertTrue(
            startBlock.contains("setupPayoutWallet("),
            "runStartOnMain обязан вызвать setupPayoutWallet после walletVc.start()",
        )
        assertTrue(
            source.contains("private suspend fun setupPayoutWallet"),
            "setupPayoutWallet должен быть private suspend fun",
        )
    }

    @Test
    fun `setupPayoutWallet вызывает addExternalWallet и updatePayoutWallet`() {
        val block = source.substringAfter("private suspend fun setupPayoutWallet")
            .substringBefore("private fun cleanupOnFailure")
        assertTrue(block.contains("addExternalWallet("), "должен вызвать addExternalWallet")
        assertTrue(block.contains("updatePayoutWallet("), "должен вызвать updatePayoutWallet")
        assertTrue(
            block.contains("walletAddress.isBlank()"),
            "пустой walletAddress — ранний возврат, JNI не дёргать",
        )
    }

    @Test
    fun `setupPayoutWallet оборачивает SDK-вызовы в runCatching — не валит старт движка`() {
        val block = source.substringAfter("private suspend fun setupPayoutWallet")
            .substringBefore("private fun cleanupOnFailure")
        assertTrue(
            block.contains("runCatching"),
            "setupPayoutWallet обязан runCatching — иначе SDK throw валит старт engine",
        )
    }

    @Test
    fun `setupPayoutWallet success логирует через Log_i не PersistentLoggers_warn`() {
        val block = source.substringAfter("private suspend fun setupPayoutWallet")
            .substringBefore("private fun cleanupOnFailure")
        val successFragment = block.substringAfter("updatePayoutWallet(walletId)")
            .substringBefore("} else {")
        assertTrue(
            successFragment.contains("Log.i") && successFragment.contains("payout wallet set"),
            "Success-event 'payout wallet set' обязан логироваться через Log.i, не PersistentLoggers.warn — " +
                "warn-level на success = ложный шум в boot.log + alerting noise. " +
                "PersistentLoggers.warn остаётся только для аномалий (walletId not found, threw).",
        )
        assertTrue(
            !successFragment.contains("PersistentLoggers.warn"),
            "Success-event не должен использовать PersistentLoggers.warn — это ошибка severity для нормального пути.",
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
    fun `connectByPreferredLocation иерархический matcher city — region — country`() {
        assertTrue(
            source.contains("private fun connectByPreferredLocation("),
            "Bridge обязан иметь connectByPreferredLocation с UrnetworkLocationSelection — " +
                "country-only matcher ломает region/city user choice.",
        )
        val findBlock = source.substringAfter("private fun findBestMatch(")
            .substringBefore("private inline fun findIn(")
        val cityIdx = findBlock.indexOf("filtered.cities")
        val regionIdx = findBlock.indexOf("filtered.regions")
        val countryIdx = findBlock.indexOf("filtered.countries")
        assertTrue(
            cityIdx in 0 until regionIdx && regionIdx in 0 until countryIdx,
            "findBestMatch обязан искать в порядке city → region → country — иначе menu пользователя " +
                "не отражает реально подключённую локацию. cityIdx=$cityIdx regionIdx=$regionIdx countryIdx=$countryIdx",
        )
    }

    @Test
    fun `findIn matchиет countryCode case-insensitive через uppercase`() {
        val findInBlock = source.substringAfter("private inline fun findIn(")
            .substringBefore("private companion object")
        assertTrue(
            findInBlock.contains(".uppercase()"),
            "findIn обязан сравнивать countryCode через uppercase — SDK может вернуть lowercase/mixed, " +
                "иначе match всегда fail для US/us/Us.",
        )
    }

    @Test
    fun `cleanupOnFailure закрывает device без throw`() {
        val cleanupBlock = source.substringAfter("private fun cleanupOnFailure")
            .substringBefore("private companion object")
        assertTrue(cleanupBlock.contains("deviceRef.getAndSet(null)"))
        assertTrue(cleanupBlock.contains("runCatching"))
    }
}
