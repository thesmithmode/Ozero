package ru.ozero.commonvpn

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Static manifest assertion: запрещает регрессии вокруг foregroundServiceType
 * для VpnService. Прошлый релиз использовал `connectedDevice` который требует
 * физический USB/Bluetooth/CDM device — Android 14+ кидал SecurityException
 * при startForeground. Lint этого не ловил (тип валидный enum), поэтому
 * проверяем тут.
 */
class ManifestContractTest {

    private val manifest by lazy {
        val moduleRoot = File(System.getProperty("user.dir") ?: ".")
        val f = File(moduleRoot, "src/main/AndroidManifest.xml")
        assertTrue(f.exists(), "common-vpn manifest не найден: $f")
        f.readText()
    }

    @Test
    fun `OzeroVpnService объявлен с foregroundServiceType specialUse или systemExempted`() {
        // Регулярка достаёт значение android:foregroundServiceType из элемента
        // с android:name=".OzeroVpnService". Принимаем оба валидных для VPN типа:
        // specialUse + meta-data subtype=vpn (наш выбор), или systemExempted
        // (зарезервировано system-app, но формально допустимо).
        val regex = Regex(
            "<service[^>]*android:name=\"\\.OzeroVpnService\"[^>]*android:foregroundServiceType=\"([^\"]+)\"",
            RegexOption.DOT_MATCHES_ALL,
        )
        val match = regex.find(manifest)
        // Возможен обратный порядок атрибутов — пробуем и его
        val match2 = if (match == null) {
            Regex(
                "<service[^>]*android:foregroundServiceType=\"([^\"]+)\"[^>]*android:name=\"\\.OzeroVpnService\"",
                RegexOption.DOT_MATCHES_ALL,
            ).find(manifest)
        } else {
            null
        }
        val type = (match ?: match2)?.groupValues?.get(1)
        val resolved = assertNotNull(type, "У <service .OzeroVpnService> должен быть android:foregroundServiceType")
        assertTrue(
            resolved in setOf("specialUse", "systemExempted"),
            "foregroundServiceType=\"$resolved\" недопустим для VPN: connectedDevice требует USB/Bluetooth, " +
                "dataSync/mediaProcessing/etc не подходят. Используй specialUse + meta-data subtype=vpn.",
        )
    }

    @Test
    fun `при specialUse есть meta-data subtype=vpn`() {
        if (!manifest.contains("foregroundServiceType=\"specialUse\"")) return
        // Android 14+ требует <property> name=PROPERTY_SPECIAL_USE_FGS_SUBTYPE для type=specialUse
        assertTrue(
            manifest.contains("PROPERTY_SPECIAL_USE_FGS_SUBTYPE"),
            "При foregroundServiceType=\"specialUse\" требуется <property name=" +
                "\"android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE\" value=\"vpn\"/>.",
        )
    }

    @Test
    fun `BIND_VPN_SERVICE permission объявлен`() {
        // Без BIND_VPN_SERVICE на <service> система не примет VPN.
        assertTrue(
            manifest.contains("android.permission.BIND_VPN_SERVICE"),
            "VpnService должен иметь android:permission=\"android.permission.BIND_VPN_SERVICE\".",
        )
    }

    @Test
    fun `intent-filter android_net_VpnService присутствует`() {
        assertTrue(
            manifest.contains("android.net.VpnService"),
            "Без <action android:name=\"android.net.VpnService\"/> система не покажет наш сервис " +
                "в системном диалоге выбора VPN.",
        )
    }

    @Test
    fun `FOREGROUND_SERVICE_CONNECTED_DEVICE permission НЕ объявлен`() {
        // Прошлый баг: эта permission парная к connectedDevice типу. Если кто-то
        // случайно вернёт permission — детектим раньше чем тип дойдёт до runtime.
        assertEquals(
            false,
            manifest.contains("FOREGROUND_SERVICE_CONNECTED_DEVICE"),
            "FOREGROUND_SERVICE_CONNECTED_DEVICE permission парный к connectedDevice типу — не нужен для VPN.",
        )
    }
}
