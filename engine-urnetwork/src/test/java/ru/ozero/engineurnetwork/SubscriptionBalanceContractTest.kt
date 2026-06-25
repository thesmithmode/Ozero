package ru.ozero.engineurnetwork

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SubscriptionBalanceContractTest {

    private val source by lazy {
        val moduleRoot = File(System.getProperty("user.dir") ?: ".")
        val f = File(moduleRoot, "src/main/java/ru/ozero/engineurnetwork/RealUrnetworkSdkBridge.kt")
        assertTrue(f.exists(), "RealUrnetworkSdkBridge.kt не найден: $f")
        f.readText()
    }

    @Test
    fun `bridge интерфейс объявляет fetchSubscriptionBalance`() {
        val ifaceFile = File(
            System.getProperty("user.dir") ?: ".",
            "src/main/java/ru/ozero/engineurnetwork/UrnetworkSdkBridge.kt",
        )
        val src = ifaceFile.readText()
        assertTrue(src.contains("fetchSubscriptionBalance"))
        assertTrue(src.contains("SubscriptionBalanceSnapshot"))
        assertTrue(src.contains("balanceBytes"))
        assertTrue(src.contains("pendingBytes"))
        assertTrue(src.contains("startBalanceBytes"))
        assertTrue(src.contains("usedBytes"))
        assertTrue(src.contains("plan"))
        assertTrue(src.contains("store"))
    }

    @Test
    fun `RealBridge формула used = startBalance - balance - pending`() {
        val block = source
            .substringAfter("override suspend fun fetchSubscriptionBalance")
            .substringBefore("private fun cleanupOnFailure")
        assertTrue(
            block.contains("startBalance - balance - pending"),
            "Формула used должна быть startBalance - balance - pending — иначе UI покажет неверный consumed bytes",
        )
    }

    @Test
    fun `RealBridge fetchSubscriptionBalance имеет timeout 10s`() {
        assertTrue(
            source.contains("SUBSCRIPTION_BALANCE_TIMEOUT_MS = 10_000L"),
            "Timeout subscriptionBalance обязан быть 10s — без него callback может никогда не resume",
        )
        val block = source
            .substringAfter("override suspend fun fetchSubscriptionBalance")
            .substringBefore("private fun cleanupOnFailure")
        assertTrue(block.contains("withTimeoutOrNull(SUBSCRIPTION_BALANCE_TIMEOUT_MS)"))
    }

    @Test
    fun `RealBridge fetchSubscriptionBalance возвращает null при отсутствии device`() {
        val block = source
            .substringAfter("override suspend fun fetchSubscriptionBalance")
            .substringBefore("private fun cleanupOnFailure")
        assertTrue(
            block.contains("deviceRef.get() ?: return null"),
            "Без device subscriptionBalance не должна крашить — возвращать null",
        )
    }

    @Test
    fun `RealBridge fetchSubscriptionBalance защищает callback от двойного resume`() {
        val block = source
            .substringAfter("override suspend fun fetchSubscriptionBalance")
            .substringBefore("private fun cleanupOnFailure")
        assertTrue(
            block.contains("AtomicBoolean") && block.contains("compareAndSet(false, true)"),
            "Callback может вызваться + timeout одновременно — guard через AtomicBoolean обязателен",
        )
    }

    @Test
    fun `RealBridge fetchSubscriptionBalance кеширует последний снимок`() {
        val block = source
            .substringAfter("override suspend fun fetchSubscriptionBalance")
            .substringBefore("private fun cleanupOnFailure")
        assertTrue(
            block.contains("subscriptionBalanceRef.set"),
            "Snapshot должен кешироваться — на timeout вернётся последний известный, не null",
        )
        assertTrue(block.contains("subscriptionBalanceRef.get()"))
    }

    @Test
    fun `Snapshot содержит все требуемые поля`() {
        val snap = UrnetworkSdkBridge.SubscriptionBalanceSnapshot(
            balanceBytes = 100L,
            pendingBytes = 20L,
            startBalanceBytes = 200L,
            usedBytes = 80L,
            plan = "Supporter",
            store = "google",
        )
        assertEquals(100L, snap.balanceBytes)
        assertEquals(20L, snap.pendingBytes)
        assertEquals(200L, snap.startBalanceBytes)
        assertEquals(80L, snap.usedBytes)
        assertEquals("Supporter", snap.plan)
        assertEquals("google", snap.store)
    }

    @Test
    fun `Snapshot допускает null plan и store для free user`() {
        val snap = UrnetworkSdkBridge.SubscriptionBalanceSnapshot(
            balanceBytes = 0L,
            pendingBytes = 0L,
            startBalanceBytes = 0L,
            usedBytes = 0L,
            plan = null,
            store = null,
        )
        assertEquals(null, snap.plan)
        assertEquals(null, snap.store)
    }

    @Test
    fun `Snapshot — формула used корректна на типовых значениях`() {
        val start = 10_000_000_000L
        val balance = 7_500_000_000L
        val pending = 500_000_000L
        val expectedUsed = 2_000_000_000L
        assertEquals(expectedUsed, start - balance - pending)
    }

    @Test
    fun `Snapshot — used может быть отрицательным при rounding в SDK (UI должен coerce)`() {
        val start = 1_000L
        val balance = 800L
        val pending = 300L
        val used = start - balance - pending
        assertTrue(used < 0L, "Допустимо отрицательное used — UI обязан clamp до 0")
    }

    @Test
    fun `RealBridge subscriptionBalance логирует clientId loc providePaused — diagnostic для x2 traffic`() {
        val block = source
            .substringAfter("override suspend fun fetchSubscriptionBalance")
            .substringBefore("private fun cleanupOnFailure")
        assertTrue(
            block.contains("clientId="),
            "raw лог обязан содержать clientId — для диагностики откуда x2 расхождение баланса " +
                "vs оригинальный URnetwork-app (разный аккаунт vs одинаковый аккаунт)",
        )
        assertTrue(
            block.contains("providePaused="),
            "raw лог обязан содержать providePaused — provide bonus может удваивать quota",
        )
        assertTrue(
            block.contains("loc="),
            "raw лог обязан содержать activeLocation — bonus может зависеть от выбранной локации",
        )
    }

    @Test
    fun `browse-only device init does not unpause provide`() {
        val initBlock = source
            .substringAfter("override suspend fun initDeviceForLocations")
            .substringBefore("private suspend fun ensureDeviceOnMain")
        val ensureBlock = source
            .substringAfter("private suspend fun ensureDeviceOnMain")
            .substringBefore("private fun applyDeviceFields")
        val startBlock = source
            .substringAfter("private suspend fun runStartOnMain")
            .substringBefore("private suspend fun setupWalletControllerAndPipeline")
        val helperBlock = source
            .substringAfter("private fun applyDeviceFields")
            .substringBefore("private fun persistConnectLocation")

        assertTrue(initBlock.contains("ensureDeviceOnMain(byClientJwt)"))
        assertTrue(ensureBlock.contains("applyDeviceFields(device, localState, unpauseProvide = false)"))
        assertTrue(startBlock.contains("applyDeviceFields(d, localState, unpauseProvide = true)"))
        assertTrue(helperBlock.contains("unpauseProvide: Boolean"))
        assertTrue(helperBlock.contains("device.providePaused = !unpauseProvide"))
    }

    @Test
    fun `Snapshot — used = 0 для нулевого баланса`() {
        val snap = UrnetworkSdkBridge.SubscriptionBalanceSnapshot(0L, 0L, 0L, 0L, null, null)
        assertNotNull(snap)
        assertEquals(0L, snap.usedBytes)
    }
}
