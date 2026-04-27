package ru.ozero.commonvpn

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ManifestContractTest {

    private val manifest by lazy {
        val moduleRoot = File(System.getProperty("user.dir") ?: ".")
        val f = File(moduleRoot, "src/main/AndroidManifest.xml")
        assertTrue(f.exists(), "common-vpn manifest не найден: $f")
        f.readText()
    }

    @Test
    fun `OzeroVpnService объявлен с foregroundServiceType specialUse или systemExempted`() {
                                        val regex = Regex(
            "<service[^>]*android:name=\"\\.OzeroVpnService\"[^>]*android:foregroundServiceType=\"([^\"]+)\"",
            RegexOption.DOT_MATCHES_ALL,
        )
        val match = regex.find(manifest)
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
                assertTrue(
            manifest.contains("PROPERTY_SPECIAL_USE_FGS_SUBTYPE"),
            "При foregroundServiceType=\"specialUse\" требуется <property name=" +
                "\"android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE\" value=\"vpn\"/>.",
        )
    }

    @Test
    fun `BIND_VPN_SERVICE permission объявлен`() {
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
                        assertEquals(
            false,
            manifest.contains("FOREGROUND_SERVICE_CONNECTED_DEVICE"),
            "FOREGROUND_SERVICE_CONNECTED_DEVICE permission парный к connectedDevice типу — не нужен для VPN.",
        )
    }
}
