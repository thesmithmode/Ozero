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
            engineAutoPriority = "warp,byedpi",
            bydpiUseUiMode = true,
            bydpiUiSettingsJson = """{"k":"v"}""",
            bydpiDefaultAccepted = false,
            urnetworkCountryCode = "DE",
        ),
        urnetwork = BackupUrnetwork(
            byJwt = "jwt-x",
            windowType = "speed",
            fixedIpSize = true,
            allowDirect = false,
            provideEnabled = true,
            provideControlMode = "always",
            provideNetworkMode = "wifi",
            selectedLocation = BackupUrnetworkLocation("ES", "Madrid", "Madrid"),
        ),
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

        assertEquals(AppBackupData.CURRENT_VERSION, restored.version)
        assertEquals("2026-05-05T12:00:00Z", restored.exportedAt)

        val s = restored.settings
        assertEquals("per_app", s.splitMode)
        assertEquals(true, s.ipv6Enabled)
        assertEquals("byedpi", s.manualEngine)
        assertEquals("--args", s.bydpiWinningArgs)
        assertEquals("warp,byedpi", s.engineAutoPriority)
        assertEquals(true, s.bydpiUseUiMode)
        assertEquals("""{"k":"v"}""", s.bydpiUiSettingsJson)
        assertEquals(false, s.bydpiDefaultAccepted)
        assertEquals("DE", s.urnetworkCountryCode)

        val u = restored.urnetwork
        assertEquals("jwt-x", u.byJwt)
        assertEquals("speed", u.windowType)
        assertEquals(true, u.fixedIpSize)
        assertEquals(false, u.allowDirect)
        assertEquals(true, u.provideEnabled)
        assertEquals("always", u.provideControlMode)
        assertEquals("wifi", u.provideNetworkMode)
        assertEquals("ES", u.selectedLocation?.countryCode)
        assertEquals("Madrid", u.selectedLocation?.region)
        assertEquals("Madrid", u.selectedLocation?.city)

        assertEquals(1, restored.warpSlots.size)
        assertEquals("slot-1", restored.warpSlots[0].id)
        assertEquals(2, restored.splitRules.size)
    }

    @Test
    fun `пустые блоки — корректно сериализуются`() {
        val data = AppBackupData(
            exportedAt = "2026-01-01T00:00:00Z",
            settings = BackupSettings(
                splitMode = null, ipv6Enabled = null, autoStart = null,
                manualEngine = null, bydpiWinningArgs = null, urnetworkEnabled = null,
                urnetworkJwt = null, customDnsServers = null, hostsMode = null,
                hostsList = null, uiLocaleTag = null, appMode = null,
            ),
            urnetwork = BackupUrnetwork(),
            warpSlots = emptyList(),
            splitRules = emptyList(),
        )
        val restored = AppBackupSerializer.deserialize(AppBackupSerializer.serialize(data))
        assertTrue(restored.warpSlots.isEmpty())
        assertTrue(restored.splitRules.isEmpty())
        assertNull(restored.settings.splitMode)
        assertNull(restored.urnetwork.byJwt)
        assertNull(restored.urnetwork.selectedLocation)
    }

    @Test
    fun `неподдерживаемая версия — бросает исключение`() {
        val json = """{"version":999,"exportedAt":"","settings":{},"urnetwork":{},"warpSlots":[],"splitRules":[]}"""
        val ex = runCatching { AppBackupSerializer.deserialize(json) }.exceptionOrNull()
        assertTrue(ex is AppBackupSerializer.BackupParseException)
        assertTrue(ex.message?.contains("999") == true)
    }

    @Test
    fun `версия 0 — также отклоняется`() {
        val json = """{"version":0,"exportedAt":"","settings":{},"urnetwork":{},"warpSlots":[],"splitRules":[]}"""
        val ex = runCatching { AppBackupSerializer.deserialize(json) }.exceptionOrNull()
        assertTrue(ex is AppBackupSerializer.BackupParseException)
    }

    @Test
    fun `malformed JSON — BackupParseException вместо crash`() {
        val ex = runCatching { AppBackupSerializer.deserialize("not a json at all") }.exceptionOrNull()
        assertTrue(ex is AppBackupSerializer.BackupParseException)
    }

    @Test
    fun `deserializeAuto rejects oversized payload`() {
        val huge = ByteArray(11 * 1024 * 1024) { 'a'.code.toByte() }
        val ex = runCatching { AppBackupSerializer.deserializeAuto(huge) }.exceptionOrNull()
        assertTrue(ex is AppBackupSerializer.BackupParseException)
    }

    @Test
    fun `AWG расширенные поля переживают roundtrip`() {
        val slot = fullData.warpSlots[0].copy(
            awgS3 = 7, awgS4 = 11, awgI1 = 13, awgI2 = 17, awgI5 = 19,
        )
        val data = fullData.copy(warpSlots = listOf(slot))
        val restored = AppBackupSerializer.deserialize(AppBackupSerializer.serialize(data))
        assertEquals(7, restored.warpSlots[0].awgS3)
        assertEquals(11, restored.warpSlots[0].awgS4)
        assertEquals(13, restored.warpSlots[0].awgI1)
        assertEquals(17, restored.warpSlots[0].awgI2)
        assertEquals(19, restored.warpSlots[0].awgI5)
    }

    @Test
    fun `AWG v2 hex blob поля I1Hex-I5Hex переживают roundtrip`() {
        val slot = fullData.warpSlots[0].copy(
            awgI1Hex = "c100deadbeef",
            awgI2Hex = "01ff",
            awgI3Hex = "ab",
            awgI4Hex = "cd",
            awgI5Hex = "1234567890",
        )
        val data = fullData.copy(warpSlots = listOf(slot))
        val restored = AppBackupSerializer.deserialize(AppBackupSerializer.serialize(data))
        assertEquals("c100deadbeef", restored.warpSlots[0].awgI1Hex)
        assertEquals("01ff", restored.warpSlots[0].awgI2Hex)
        assertEquals("ab", restored.warpSlots[0].awgI3Hex)
        assertEquals("cd", restored.warpSlots[0].awgI4Hex)
        assertEquals("1234567890", restored.warpSlots[0].awgI5Hex)
    }

    @Test
    fun `serializeEncrypted и deserializeAuto roundtrip`() {
        val bytes = AppBackupSerializer.serializeEncrypted(fullData)
        val restored = AppBackupSerializer.deserializeAuto(bytes)
        assertEquals(fullData.exportedAt, restored.exportedAt)
        assertEquals(fullData.warpSlots[0].privateKey, restored.warpSlots[0].privateKey)
        assertEquals(fullData.urnetwork.byJwt, restored.urnetwork.byJwt)
    }

    @Test
    fun `deserializeAuto принимает legacy plaintext JSON`() {
        val json = AppBackupSerializer.serialize(fullData)
        val restored = AppBackupSerializer.deserializeAuto(json.toByteArray(Charsets.UTF_8))
        assertEquals(fullData.exportedAt, restored.exportedAt)
    }

    @Test
    fun `v1 backup — strategy null, новые поля null`() {
        val json = """{"version":1,"exportedAt":"old","settings":{},"urnetwork":{},"warpSlots":[],"splitRules":[]}"""
        val restored = AppBackupSerializer.deserialize(json)
        assertEquals(1, restored.version)
        assertNull(restored.strategy)
        assertNull(restored.settings.engineAutoPriority)
        assertNull(restored.urnetwork.selectedLocation)
    }

    @Test
    fun `v2 backup — walletOverride игнорируется при чтении`() {
        val json = """{"version":2,"exportedAt":"","settings":{},""" +
            """"urnetwork":{"walletOverride":"0xdead","byJwt":"j"},"warpSlots":[],"splitRules":[]}"""
        val restored = AppBackupSerializer.deserialize(json)
        assertEquals("j", restored.urnetwork.byJwt)
    }

    @Test
    fun `strategy roundtrip — evolutionTargetFitness переживает`() {
        val strategy = BackupStrategy(
            settings = BackupStrategySettings(
                requestsPerDomain = 2, evolutionMode = true,
                evolutionMutationRate = 0.3f, evolutionTargetFitness = 0.95f,
            ),
            domainLists = listOf(BackupDomainList("g", "General", listOf("b.com"), true, true)),
            savedStrategies = listOf(BackupSavedStrategy("s1", "-K -An", null, true)),
        )
        val data = fullData.copy(strategy = strategy)
        val restored = AppBackupSerializer.deserialize(AppBackupSerializer.serialize(data))
        assertEquals(0.95f, restored.strategy?.settings?.evolutionTargetFitness)
        assertEquals(0.3f, restored.strategy?.settings?.evolutionMutationRate)
        assertEquals(1, restored.strategy?.domainLists?.size)
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
