package ru.ozero.app.ui.urnetwork

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UrnetworkBalanceCardSentinelTest {

    private val source by lazy {
        File(
            System.getProperty("user.dir") ?: ".",
            "src/main/java/ru/ozero/app/ui/urnetwork/UrnetworkBalanceCard.kt",
        ).readText()
    }

    @Test
    fun `BalanceCard не применяет cap — отображает реальный баланс от бэкенда`() {
        val balanceDetailsBody = source
            .substringAfter("private fun BalanceDetails(")
            .substringBefore("private fun TrafficProgressBar(")
        assertFalse(
            balanceDetailsBody.contains("FREE_TIER_CAP_BYTES"),
            "BalanceDetails не должен применять FREE_TIER_CAP_BYTES — баланс показывается как есть от бэкенда",
        )
        assertFalse(
            source.contains("FREE_TIER_CAP_BYTES"),
            "FREE_TIER_CAP_BYTES не должен существовать — cap удалён намеренно чтобы показывать бонусный трафик",
        )
    }

    @Test
    fun `BalanceDetails использует coerceAtLeast для защиты от отрицательных значений`() {
        val balanceDetailsBody = source
            .substringAfter("private fun BalanceDetails(")
            .substringBefore("private fun TrafficProgressBar(")
        assertTrue(
            balanceDetailsBody.contains("coerceAtLeast(0L)"),
            "displayBalance и displayStart обязаны использовать coerceAtLeast(0L) — бэкенд может вернуть отрицательное значение",
        )
    }
}
