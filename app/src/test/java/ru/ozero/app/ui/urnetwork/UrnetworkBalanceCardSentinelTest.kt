package ru.ozero.app.ui.urnetwork

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertTrue

class UrnetworkBalanceCardSentinelTest {

    private val source by lazy {
        File(
            System.getProperty("user.dir") ?: ".",
            "src/main/java/ru/ozero/app/ui/urnetwork/UrnetworkBalanceCard.kt",
        ).readText()
    }

    @Test
    fun `FREE_TIER_CAP_BYTES равен 34 GiB — защита от случайного изменения значения`() {
        assertTrue(
            source.contains("34L * 1024L * 1024L * 1024L"),
            "FREE_TIER_CAP_BYTES обязан быть 34 GiB (34L * 1024L * 1024L * 1024L) — " +
                "URnetwork бэкенд выдаёт ровно 34 GiB на free tier за период",
        )
    }

    @Test
    fun `BalanceDetails применяет FREE_TIER_CAP_BYTES — защита от регрессии overlap display`() {
        val balanceDetailsBody = source
            .substringAfter("private fun BalanceDetails(")
            .substringBefore("private fun TrafficProgressBar(")
        assertTrue(
            balanceDetailsBody.contains("FREE_TIER_CAP_BYTES"),
            "BalanceDetails обязан применять FREE_TIER_CAP_BYTES — иначе overlap window (30h TTL + 24h крон) " +
                "показывает 68-102 GiB вместо 34 GiB",
        )
        assertTrue(
            balanceDetailsBody.contains("minOf("),
            "displayBalance и displayStart обязаны вычисляться через minOf — кэп не работает без min",
        )
    }
}
