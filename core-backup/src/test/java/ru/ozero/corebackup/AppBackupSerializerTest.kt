package ru.ozero.corebackup

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppBackupSerializerTest {

    private val fullData = AppBackupData(
        exportedAt = "2026-05-05T12:00:00Z",
        settings = BackupSettings(
            splitMode = "per_app",
            ipv6Enabled = true,
            autoStart = false,
            manualEngine = "byedpi",
            bydpiWinningArgs = "--args",
            urnetworkEnabled = false,
            urnetworkJwt = null,
            customDnsServers = "8.8.8.8",
            hostsMode = "blacklist",
            hostsList = "example.com",
            uiLocaleTag = "ru",
            appMode = "manual",
        ),
        urnetwork = BackupUrnetwork(walletOverride = "0xabc", byJwt = null),
        warpSlots = listOf(
            BackupWarpSlot(
                id = "slot-1",
                name = "Home",
                isActive = true,
                privateKey = "privKey",
                publicKey = "pubKey",
                peerPublicKey = "peerPub",
                peerEndpoint = "engage.cloudflareclient.com:2408",
                interfaceAddressV4 = "172.16.0.2/32",
                interfaceAddressV6 = "2606::1/128",
                accountLicense = "lic",
                mtu = 1280,
                dnsServers = listOf("1.1.1.1"),
                keepaliveSeconds = 25,
                awgJc = 5, awgJmin = 100, awgJmax = 200, awgS1 = 0, awgS2 = 0,
                awgH1 = 1L, awgH2 = 2L, awgH3 = 3L, awgH4 = 4L,
            ),
        ),
        splitRules = listOf(
            BackupSplitRule("com.example", true),
            BackupSplitRule("com.other", false),
        ),
    )

    @Test
    fun `serialize then deserialize — полный roundtrip`() {
        val json = AppBackupSerializer.serialize(fullData)
        val restored = AppBackupSerializer.deserialize(json)

        assertEquals(1, restored.version)
        assertEquals("2026-05-05T12:00:00Z", restored.exportedAt)

        val s = restored.settings
        assertEquals("per_app", s.splitMode)
        assertEquals(true, s.ipv6Enabled)
        assertEquals(false, s.autoStart)
        assertEquals("byedpi", s.manualEngine)
        assertEquals("--args", s.bydpiWinningArgs)
        assertEquals(false, s.urnetworkEnabled)
        assertNull(s.urnetworkJwt)
        assertEquals("8.8.8.8", s.customDnsServers)
        assertEquals("blacklist", s.hostsMode)
        assertEquals("example.com", s.hostsList)
        assertEquals("ru", s.uiLocaleTag)
        assertEquals("manual", s.appMode)

        assertEquals("0xabc", restored.urnetwork.walletOverride)
        assertNull(restored.urnetwork.byJwt)

        assertEquals(1, restored.warpSlots.size)
        val slot = restored.warpSlots[0]
        assertEquals("slot-1", slot.id)
        assertEquals("Home", slot.name)
        assertTrue(slot.isActive)
        assertEquals("privKey", slot.privateKey)
        assertEquals("pubKey", slot.publicKey)
        assertEquals("peerPub", slot.peerPublicKey)
        assertEquals("engage.cloudflareclient.com:2408", slot.peerEndpoint)
        assertEquals("172.16.0.2/32", slot.interfaceAddressV4)
        assertEquals("2606::1/128", slot.interfaceAddressV6)
        assertEquals("lic", slot.accountLicense)
        assertEquals(1280, slot.mtu)
        assertEquals(listOf("1.1.1.1"), slot.dnsServers)
        assertEquals(25, slot.keepaliveSeconds)
        assertEquals(5, slot.awgJc)
        assertEquals(100, slot.awgJmin)
        assertEquals(200, slot.awgJmax)
        assertEquals(0, slot.awgS1)
        assertEquals(0, slot.awgS2)
        assertEquals(1L, slot.awgH1)
        assertEquals(2L, slot.awgH2)
        assertEquals(3L, slot.awgH3)
        assertEquals(4L, slot.awgH4)

        assertEquals(2, restored.splitRules.size)
        assertEquals("com.example", restored.splitRules[0].packageName)
        assertTrue(restored.splitRules[0].isExcluded)
        assertEquals("com.other", restored.splitRules[1].packageName)
    }

    @Test
    fun `пустые слоты и правила — корректно сериализуются`() {
        val data = AppBackupData(
            exportedAt = "2026-01-01T00:00:00Z",
            settings = BackupSettings(
                splitMode = null, ipv6Enabled = null, autoStart = null,
                manualEngine = null, bydpiWinningArgs = null, urnetworkEnabled = null,
                urnetworkJwt = null, customDnsServers = null, hostsMode = null,
                hostsList = null, uiLocaleTag = null, appMode = null,
            ),
            urnetwork = BackupUrnetwork(walletOverride = null, byJwt = null),
            warpSlots = emptyList(),
            splitRules = emptyList(),
        )
        val restored = AppBackupSerializer.deserialize(AppBackupSerializer.serialize(data))
        assertTrue(restored.warpSlots.isEmpty())
        assertTrue(restored.splitRules.isEmpty())
        assertNull(restored.settings.splitMode)
        assertNull(restored.urnetwork.walletOverride)
    }

    @Test
    fun `неподдерживаемая версия — бросает исключение`() {
        val json = """{"version":999,"exportedAt":"","settings":{},"urnetwork":{},"warpSlots":[],"splitRules":[]}"""
        val ex = runCatching { AppBackupSerializer.deserialize(json) }.exceptionOrNull()
        assertTrue(ex != null)
        assertTrue(ex.message?.contains("999") == true)
    }

    @Test
    fun `serializeEncrypted и deserializeAuto roundtrip`() {
        val bytes = AppBackupSerializer.serializeEncrypted(fullData)
        val restored = AppBackupSerializer.deserializeAuto(bytes)
        assertEquals(fullData.exportedAt, restored.exportedAt)
        assertEquals(fullData.warpSlots[0].privateKey, restored.warpSlots[0].privateKey)
        assertEquals(fullData.urnetwork.walletOverride, restored.urnetwork.walletOverride)
    }

    @Test
    fun `deserializeAuto принимает legacy plaintext JSON`() {
        val json = AppBackupSerializer.serialize(fullData)
        val restored = AppBackupSerializer.deserializeAuto(json.toByteArray(Charsets.UTF_8))
        assertEquals(fullData.exportedAt, restored.exportedAt)
    }

    @Test
    fun `несколько слотов — порядок сохраняется`() {
        val makeSlot = { n: Int ->
            BackupWarpSlot(
                id = "id-$n", name = "Slot $n", isActive = n == 1,
                privateKey = "pk$n", publicKey = "", peerPublicKey = "pp",
                peerEndpoint = "ep:2408", interfaceAddressV4 = "10.0.0.$n/32",
                interfaceAddressV6 = "::", accountLicense = "", mtu = 1280,
                dnsServers = listOf("1.1.1.1"), keepaliveSeconds = 25,
                awgJc = 0, awgJmin = 0, awgJmax = 0, awgS1 = 0, awgS2 = 0,
                awgH1 = 0L, awgH2 = 0L, awgH3 = 0L, awgH4 = 0L,
            )
        }
        val data = fullData.copy(warpSlots = listOf(makeSlot(1), makeSlot(2), makeSlot(3)))
        val restored = AppBackupSerializer.deserialize(AppBackupSerializer.serialize(data))
        assertEquals(3, restored.warpSlots.size)
        assertEquals("id-1", restored.warpSlots[0].id)
        assertEquals("id-2", restored.warpSlots[1].id)
        assertEquals("id-3", restored.warpSlots[2].id)
    }
}
