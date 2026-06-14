package ru.ozero.enginewarp

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import ru.ozero.enginescore.VpnSocketProtector
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class WarpRuntimeFingerprintTest {

    @Test
    fun `fingerprint changes for runtime fields and does not expose secrets`() {
        val slot = WarpConfigSlot(
            id = "slot-1",
            name = "WARP",
            config = config(
                privateKey = "sample-warp-key-a",
                dnsServers = listOf("1.1.1.1"),
            ),
            rawIniOverride = "PrivateKey = sample-warp-key-a",
            endpointList = listOf("162.159.193.10:2408"),
        )

        val changed = slot.copy(
            config = slot.config.copy(dnsServers = listOf("1.0.0.1")),
        )
        val secretChanged = slot.copy(
            config = slot.config.copy(privateKey = "sample-warp-key-b"),
        )
        val rawChanged = slot.copy(rawIniOverride = "PrivateKey = sample-warp-key-b")

        assertNotEquals(slot.runtimeFingerprint(), changed.runtimeFingerprint())
        assertNotEquals(slot.runtimeFingerprint(), secretChanged.runtimeFingerprint())
        assertNotEquals(slot.runtimeFingerprint(), rawChanged.runtimeFingerprint())
        assertTrue(slot.runtimeFingerprint().toString().contains("sample-warp-key-a").not())
        assertTrue(slot.runtimeFingerprint().toString().contains("digest=***"))
    }

    @Test
    fun `fingerprint covers empty optional lists`() {
        val base = WarpConfigSlot(
            id = "slot-empty",
            name = "WARP",
            config = config(
                privateKey = "sample-warp-key-a",
                dnsServers = emptyList(),
            ).copy(allowedIps = emptyList()),
            rawIniOverride = null,
            endpointList = emptyList(),
        )
        val changedDns = base.copy(config = base.config.copy(dnsServers = listOf("1.1.1.1")))
        val changedAllowed = base.copy(config = base.config.copy(allowedIps = listOf("0.0.0.0/0")))
        val changedEndpointList = base.copy(endpointList = listOf("162.159.193.10:2408"))

        assertNotEquals(base.runtimeFingerprint(), changedDns.runtimeFingerprint())
        assertNotEquals(base.runtimeFingerprint(), changedAllowed.runtimeFingerprint())
        assertNotEquals(base.runtimeFingerprint(), changedEndpointList.runtimeFingerprint())
    }

    @Test
    fun `fingerprint ignores raw ini formatting noise but tracks endpoint order`() {
        val base = WarpConfigSlot(
            id = "slot-normalized",
            name = "WARP",
            config = config(privateKey = "sample-warp-key-a", dnsServers = listOf("1.1.1.1")),
            rawIniOverride = """
                [Interface]
                PrivateKey = sample-warp-key-a
                Address = 10.0.0.2/32
                DNS = 1.1.1.1

                [Peer]
                PublicKey = peer-public
                AllowedIPs = 0.0.0.0/0, ::/0
                Endpoint = engage.cloudflareclient.com:2408
                PersistentKeepalive = 25
            """.trimIndent(),
            endpointList = listOf("162.159.193.10:2408", "162.159.193.11:2408"),
        )
        val formatted = base.copy(
            rawIniOverride = """
                [Interface]
                # comment
                PrivateKey = sample-warp-key-a
                Address = 10.0.0.2/32
                DNS = 1.1.1.1

                [Peer]
                PublicKey = peer-public
                AllowedIPs = 0.0.0.0/0, ::/0
                Endpoint = engage.cloudflareclient.com:2408
                PersistentKeepalive = 25
            """.trimIndent(),
            endpointList = listOf("162.159.193.11:2408", "162.159.193.10:2408"),
        )

        assertNotEquals(base.runtimeFingerprint(), formatted.runtimeFingerprint())
    }

    @Test
    fun `fingerprint changes when raw ini carries unmodeled peer fields`() {
        val base = WarpConfigSlot(
            id = "slot-extra",
            name = "WARP",
            config = config(privateKey = "sample-warp-key-a", dnsServers = listOf("1.1.1.1")),
            rawIniOverride = """
                [Interface]
                PrivateKey = sample-warp-key-a
                Address = 10.0.0.2/32

                [Peer]
                PublicKey = sample-peer-key
                Endpoint = engage.cloudflareclient.com:2408
                PresharedKey = secret-a
            """.trimIndent(),
            endpointList = listOf("162.159.193.10:2408"),
        )
        val changed = base.copy(
            rawIniOverride = """
                [Interface]
                PrivateKey = sample-warp-key-a
                Address = 10.0.0.2/32

                [Peer]
                PublicKey = sample-peer-key
                Endpoint = engage.cloudflareclient.com:2408
                PresharedKey = secret-b
            """.trimIndent(),
        )

        assertNotEquals(base.runtimeFingerprint(), changed.runtimeFingerprint())
    }

    @Test
    fun `fingerprint ignores slot identity when runtime config is unchanged`() {
        val config = config(privateKey = "sample-warp-key-a", dnsServers = listOf("1.1.1.1"))
        val first = WarpConfigSlot(
            id = "slot-a",
            name = "WARP",
            config = config,
            rawIniOverride = "ini",
            endpointList = listOf("162.159.193.10:2408"),
        )
        val second = first.copy(id = "slot-b")

        assertEquals(first.runtimeFingerprint(), second.runtimeFingerprint())
    }

    @Test
    fun `fingerprint equality ignores slot id but rejects unrelated objects`() {
        val config = config(privateKey = "sample-warp-key-a", dnsServers = listOf("1.1.1.1"))
        val first = WarpConfigSlot(
            id = "slot-a",
            name = "WARP",
            config = config,
            rawIniOverride = null,
            endpointList = emptyList(),
        ).runtimeFingerprint()
        val second = WarpConfigSlot(
            id = "slot-b",
            name = "WARP copy",
            config = config,
            rawIniOverride = null,
            endpointList = emptyList(),
        ).runtimeFingerprint()

        assertEquals(first, second)
        assertEquals(first.hashCode(), second.hashCode())
        assertTrue(first.toString() != "same-digest")
    }

    @Test
    fun `fingerprint canonicalizes endpoint whitespace and duplicates`() {
        val config = config(privateKey = "sample-warp-key-a", dnsServers = listOf("1.1.1.1"))
        val first = WarpConfigSlot(
            id = "slot-a",
            name = "WARP",
            config = config,
            rawIniOverride = null,
            endpointList = listOf(" 162.159.193.10:2408 ", "", "162.159.193.10:2408"),
        )
        val second = first.copy(endpointList = listOf("162.159.193.10:2408"))

        assertEquals(first.runtimeFingerprint(), second.runtimeFingerprint())
    }

    @Test
    fun `fingerprint preserves malformed raw ini fallback exactly`() {
        val base = WarpConfigSlot(
            id = "slot-raw",
            name = "WARP",
            config = config(privateKey = "sample-warp-key-a", dnsServers = listOf("1.1.1.1")),
            rawIniOverride = "[Interface\nPrivateKey = sample-warp-key-a",
            endpointList = emptyList(),
        )
        val changed = base.copy(rawIniOverride = "[Interface\nPrivateKey = sample-warp-key-b")

        assertNotEquals(base.runtimeFingerprint(), changed.runtimeFingerprint())
    }

    @Test
    fun `fingerprint tracks unknown raw ini sections and bare extra lines`() {
        val base = WarpConfigSlot(
            id = "slot-extra-section",
            name = "WARP",
            config = config(privateKey = "sample-warp-key-a", dnsServers = listOf("1.1.1.1")),
            rawIniOverride = """
                [Interface]
                PrivateKey = sample-warp-key-a
                Address = 172.16.0.2/32
                CustomInterface = a
                [Peer]
                PublicKey = sample-peer-key
                Endpoint = engage.cloudflareclient.com:2408
                [Unknown]
                BareLine
            """.trimIndent(),
            endpointList = emptyList(),
        )
        val changed = base.copy(
            rawIniOverride = base.rawIniOverride?.replace("BareLine", "OtherBareLine"),
        )

        assertNotEquals(base.runtimeFingerprint(), changed.runtimeFingerprint())
    }

    @Test
    fun `WarpSdkBridge default proxy methods are explicit unsupported noops`() = runTest {
        val bridge = object : WarpSdkBridge {
            override suspend fun attachTun(
                tunnelName: String,
                tunFd: Int,
                iniConfig: String,
                uapiPath: String,
                protector: VpnSocketProtector,
            ): WarpSdkBridge.AttachResult = WarpSdkBridge.AttachResult.Success

            override suspend fun detachTun() = Unit
            override fun isRunning(): Boolean = false
            override fun reprotectSockets() = Unit
        }

        val proxy = bridge.startProxy(
            tunnelName = "warp",
            iniConfig = "[Interface]",
            uapiPath = "/tmp/uapi.sock",
            socksPort = 1080,
            protector = VpnSocketProtector { true },
        )

        assertIs<WarpSdkBridge.ProxyResult.Failed>(proxy)
        assertTrue(proxy.reason.contains("not supported"))
        bridge.stopProxy()
    }

    private fun config(
        privateKey: String,
        dnsServers: List<String>,
    ) = WarpConfig(
        privateKey = privateKey,
        publicKey = "public",
        peerPublicKey = "peer-public",
        peerEndpoint = "162.159.193.10:2408",
        interfaceAddressV4 = "172.16.0.2/32",
        interfaceAddressV6 = "2606:4700:110:abcd::1/128",
        accountLicense = "license",
        dnsServers = dnsServers,
    )
}
