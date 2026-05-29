package ru.ozero.engineurnetwork

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertTrue

class UrnetworkPayoutPipelineSentinelTest {

    private val setupSource by lazy {
        val moduleRoot = File(System.getProperty("user.dir") ?: ".")
        val f = File(moduleRoot, "src/main/java/ru/ozero/engineurnetwork/UrnetworkPayoutWalletSetup.kt")
        assertTrue(f.exists(), "UrnetworkPayoutWalletSetup.kt не найден: $f")
        f.readText()
    }

    private val bridgeSource by lazy {
        val moduleRoot = File(System.getProperty("user.dir") ?: ".")
        val f = File(moduleRoot, "src/main/java/ru/ozero/engineurnetwork/RealUrnetworkSdkBridge.kt")
        assertTrue(f.exists(), "RealUrnetworkSdkBridge.kt не найден: $f")
        f.readText()
    }

    @Test
    fun `external wallet registration uses upstream SOL chain token`() {
        assertTrue(
            setupSource.contains("WALLET_BLOCKCHAIN_SOLANA = \"SOL\""),
            "addExternalWallet должен получать chain=\"SOL\" как upstream WalletViewModel; " +
                "\"solana\" даёт registration ack без привязки registry id.",
        )
    }

    @Test
    fun `WALLET_ADD_TIMEOUT_MS равен 30 секундам — backend race на регистрацию external wallet`() {
        val regex = Regex("WALLET_ADD_TIMEOUT_MS\\s*=\\s*(\\d[\\d_]*)L")
        val m = regex.find(setupSource) ?: error("WALLET_ADD_TIMEOUT_MS не найден")
        val ms = m.groupValues[1].replace("_", "").toLong()
        assertTrue(
            ms == 30_000L,
            "WALLET_ADD_TIMEOUT_MS обязан быть 30_000L — backend URnetwork race при addExternalWallet " +
                "ловит 10s timeout у части устройств → registry id not resolved → device не привязал endpoint. " +
                "30s покрывает большинство backend-задержек. Fact=$ms",
        )
    }

    @Test
    fun `configure возвращает Boolean — caller должен знать bound или deferred`() {
        val pattern = Regex("suspend fun configure\\([^)]+\\)\\s*:\\s*Boolean\\s*\\{")
        assertTrue(
            pattern.containsMatchIn(setupSource),
            "configure(walletVc, walletAddress) обязан возвращать Boolean — bridge должен " +
                "различать success/blocked для telemetry 'relay sharing: endpoint bound/deferred'.",
        )
    }

    @Test
    fun `bridge логирует relay sharing endpoint bound — telemetry готовности pipeline`() {
        assertTrue(
            bridgeSource.contains("relay sharing: endpoint bound"),
            "bridge обязан логировать 'relay sharing: endpoint bound — accumulator armed' " +
                "после успешного configure() — иначе юзер не видит подтверждения что routing подключён.",
        )
    }

    @Test
    fun `bridge логирует relay sharing endpoint deferred — telemetry deferred-state`() {
        assertTrue(
            bridgeSource.contains("relay sharing: endpoint deferred"),
            "bridge обязан логировать 'relay sharing: endpoint deferred' когда configure вернул false — " +
                "юзер видит что регистрация отложена + автоматически retry на следующем старте.",
        )
    }

    @Test
    fun `bridge логирует relay sharing traffic forwarded — первая ненулевая unpaidByteCount`() {
        assertTrue(
            bridgeSource.contains("relay sharing: traffic forwarded"),
            "bridge обязан логировать 'relay sharing: traffic forwarded' при первом >0 значении " +
                "addUnpaidByteCountListener — это безусловный сигнал что mesh forwardит трафик " +
                "external peer'у. Без него юзер не отличит «relay включён» от «relay не получает peers».",
        )
        assertTrue(
            bridgeSource.contains("sharingTrafficLogged"),
            "bridge обязан иметь sharingTrafficLogged AtomicBoolean — один лог за сессию, не флуд.",
        )
    }

    @Test
    fun `bridge сбрасывает sharingTrafficLogged в stopUnderLock — лог сработает на новой сессии`() {
        val body = bridgeSource.substringAfter("private suspend fun stopUnderLock()")
            .substringBefore("private fun closeDevice")
        assertTrue(
            body.contains("sharingTrafficLogged.set(false)"),
            "stopUnderLock обязан сбрасывать sharingTrafficLogged — иначе после stop+start " +
                "следующий «traffic forwarded» лог не сработает (флаг живёт между сессиями).",
        )
    }
}
