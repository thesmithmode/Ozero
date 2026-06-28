package ru.ozero.engineurnetwork

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertTrue

class UrnetworkPayoutPipelineSentinelTest {

    private val bridgeSource by lazy {
        val moduleRoot = File(System.getProperty("user.dir") ?: ".")
        val f = File(moduleRoot, "src/main/java/ru/ozero/engineurnetwork/RealUrnetworkSdkBridge.kt")
        assertTrue(f.exists(), "RealUrnetworkSdkBridge.kt не найден: $f")
        f.readText()
    }

    @Test
    fun `bridge не регистрирует и не выбирает payout wallet на start`() {
        val startBlock = bridgeSource.substringAfter("private suspend fun runStartOnMain")
            .substringBefore("override suspend fun stop")
        assertTrue(!startBlock.contains("addExternal" + "Wallet"))
        assertTrue(!startBlock.contains("updatePayout" + "Wallet"))
        assertTrue(!startBlock.contains("UrnetworkPayout" + "WalletSetup"))
    }

    @Test
    fun `bridge открывает wallet controller только для metrics pipeline`() {
        val body = bridgeSource.substringAfter("private suspend fun setupWalletControllerAndPipeline")
            .substringBefore("override suspend fun stop")
        assertTrue(body.contains("openWalletViewController"))
        assertTrue(body.contains("addUnpaidByteCountListener"))
        assertTrue(body.contains("fetchTransferStats"))
        assertTrue(!body.contains("addExternal" + "Wallet"))
        assertTrue(!body.contains("updatePayout" + "Wallet"))
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
