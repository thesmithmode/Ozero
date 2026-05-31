package ru.ozero.enginewarp

import org.junit.jupiter.api.Test
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
