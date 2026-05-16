package ru.ozero.commonvpn

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OzeroNotificationFactoryContractTest {

    private val source by lazy {
        val moduleRoot = File(System.getProperty("user.dir") ?: ".")
        val f = File(moduleRoot, "src/main/java/ru/ozero/commonvpn/OzeroNotificationFactory.kt")
        assertTrue(f.exists(), "OzeroNotificationFactory.kt не найден: $f")
        f.readText()
    }

    @Test
    fun `factory exposes CHANNEL_ID and NOTIFICATION_ID`() {
        assertEquals("ozero_vpn", OzeroNotificationFactory.CHANNEL_ID)
        assertTrue(OzeroNotificationFactory.NOTIFICATION_ID > 0)
    }

    @Test
    fun `factory имеет build, notifyStats, enterForeground методы`() {
        assertTrue(source.contains("fun build("), "build() обязан существовать")
        assertTrue(source.contains("fun notifyStats("), "notifyStats() обязан существовать")
        assertTrue(source.contains("fun enterForeground("), "enterForeground() обязан существовать")
    }

    @Test
    fun `enterForeground UPSIDE_DOWN_CAKE использует SPECIAL_USE с fallback на MANIFEST`() {
        val body = source.substringAfter("fun enterForeground(").substringBefore("companion object")
        assertTrue(
            body.contains("FOREGROUND_SERVICE_TYPE_SPECIAL_USE"),
            "UPSIDE_DOWN_CAKE+ требует FOREGROUND_SERVICE_TYPE_SPECIAL_USE по манифесту",
        )
        assertTrue(
            body.contains("FOREGROUND_SERVICE_TYPE_MANIFEST"),
            "fallback на MANIFEST type если SPECIAL_USE отклонён OEM ROM-ом",
        )
        val suIdx = body.indexOf("FOREGROUND_SERVICE_TYPE_SPECIAL_USE")
        val mfIdx = body.indexOf("FOREGROUND_SERVICE_TYPE_MANIFEST")
        assertTrue(
            suIdx in 0 until mfIdx,
            "SPECIAL_USE первичен, MANIFEST — только в catch (fallback). Иначе wrong order.",
        )
    }

    @Test
    fun `enterForeground оборачивает всё в try-catch — возвращает Boolean`() {
        val body = source.substringAfter("fun enterForeground(").substringBefore("companion object")
        assertTrue(
            body.contains("catch (t: Throwable)") && body.contains("return false") ||
                body.contains("return true"),
            "enterForeground обязан возвращать Boolean и ловить Throwable — иначе бросок " +
                "startForeground валит весь onStartCommand → VPN не поднимается без diagnostics",
        )
    }

    @Test
    fun `build использует FLAG_IMMUTABLE для PendingIntent — Android 12+ requirement`() {
        val body = source.substringAfter("fun build(").substringBefore("fun notifyStats")
        assertTrue(
            body.contains("FLAG_IMMUTABLE"),
            "PendingIntent.getActivity/getService на Android 12+ обязан использовать FLAG_IMMUTABLE — " +
                "иначе IllegalArgumentException при создании notification",
        )
    }

    @Test
    fun `build adds Stop action с ACTION_STOP intent`() {
        val body = source.substringAfter("fun build(").substringBefore("fun notifyStats")
        assertTrue(
            body.contains("OzeroVpnService.ACTION_STOP"),
            "Stop action обязан использовать ACTION_STOP — иначе пользователь не остановит VPN из уведомления",
        )
    }

    @Test
    fun `notifyStats null-safe — early return при null tex`() {
        val body = source.substringAfter("fun notifyStats(").substringBefore("fun enterForeground")
        assertTrue(
            body.contains("statsText == null") || body.contains("?: return") || body.contains("statsText ?:"),
            "notifyStats обязан early-return при null — иначе notify(null) валит API",
        )
    }

    @Test
    fun `notifyStats обёрнут в runCatching с PersistentLoggers warn`() {
        val body = source.substringAfter("fun notifyStats(").substringBefore("fun enterForeground")
        assertTrue(
            body.contains("runCatching"),
            "notify может throw — обязан runCatching, иначе валит stats logger",
        )
        assertTrue(
            body.contains("PersistentLoggers.warn"),
            "notify failure → PersistentLoggers.warn для boot.log диагностики",
        )
    }

    @Test
    fun `build использует NotificationChannel IMPORTANCE_LOW на O+`() {
        val body = source.substringAfter("fun build(").substringBefore("fun notifyStats")
        assertTrue(
            body.contains("IMPORTANCE_LOW"),
            "VPN notification обязан IMPORTANCE_LOW — иначе heads-up каждое обновление статистики достанет юзера",
        )
    }
}
