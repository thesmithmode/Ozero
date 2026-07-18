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
        val hasCatch = body.contains("catch (t: Throwable)")
        val hasBoolean = body.contains("return true") ||
            body.contains("return false") ||
            (body.contains("\n            true\n") && body.contains("\n            false\n")) ||
            (body.contains(" true\n") && body.contains(" false\n"))
        assertTrue(
            hasCatch && hasBoolean,
            "enterForeground обязан возвращать Boolean и ловить Throwable — иначе бросок " +
                "startForeground валит весь onStartCommand → VPN не поднимается без diagnostics",
        )
    }

    @Test
    fun `build использует FLAG_IMMUTABLE для PendingIntent — Android 12+ requirement`() {
        val body = source.substringAfter("fun build(").substringBefore("fun notifyStats")
        assertTrue(
            body.contains("FLAG_IMMUTABLE"),
            "PendingIntent.getActivity на Android 12+ обязан использовать FLAG_IMMUTABLE — " +
                "иначе IllegalArgumentException при создании notification",
        )
    }

    @Test
    fun `build does not expose direct stop service PendingIntent`() {
        val body = source.substringAfter("fun build(").substringBefore("fun notifyStats")
        assertTrue(
            !body.contains("OzeroVpnService.ACTION_STOP") &&
                !body.contains("PendingIntent.getService") &&
                !body.contains(".addAction("),
            "VPN notification must not publish delegated ACTION_STOP service PendingIntent",
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
            body.contains("PersistentLoggers.debug"),
            "notify failure → PersistentLoggers.debug для boot.log диагностики (не warn — success path)",
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
