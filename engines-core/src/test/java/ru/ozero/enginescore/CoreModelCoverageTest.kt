package ru.ozero.enginescore

import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CoreModelCoverageTest {

    @Test
    fun `TunSpec defaults describe IPv4 only routed tunnel`() {
        val spec = TunSpec(
            sessionName = "ozero",
            mtu = 1280,
            blocking = true,
            ipv4Address = "10.0.0.2",
            ipv4PrefixLength = 32,
            dnsServers = listOf("1.1.1.1"),
        )

        assertEquals("ozero", spec.sessionName)
        assertEquals(1280, spec.mtu)
        assertEquals(true, spec.blocking)
        assertEquals("10.0.0.2", spec.ipv4Address)
        assertEquals(32, spec.ipv4PrefixLength)
        assertEquals(listOf("1.1.1.1"), spec.dnsServers)
        assertEquals(true, spec.allowFamilyV4)
        assertEquals(false, spec.allowFamilyV6)
        assertNull(spec.ipv6Address)
        assertEquals(0, spec.ipv6PrefixLength)
        assertEquals(false, spec.excludeRfc1918)
        assertEquals(true, spec.routeAllV4)
        assertEquals(false, spec.routeAllV6)
        assertEquals(emptyList(), spec.routeCidrsV4)
        assertEquals(emptyList(), spec.routeCidrsV6)
        assertEquals(true, spec.excludeSelf)
    }

    @Test
    fun `TunSpec copy equality and toString include routing fields`() {
        val spec = TunSpec(
            sessionName = "ozero6",
            mtu = 1420,
            blocking = false,
            ipv4Address = "10.0.0.2",
            ipv4PrefixLength = 32,
            dnsServers = listOf("9.9.9.9"),
            allowFamilyV6 = true,
            ipv6Address = "fd00::2",
            ipv6PrefixLength = 128,
            excludeRfc1918 = true,
            routeAllV4 = false,
            routeAllV6 = true,
            routeCidrsV4 = listOf("10.0.0.0/8"),
            routeCidrsV6 = listOf("::/0"),
        )

        val copy = spec.copy(mtu = 1280)

        assertNotEquals(spec, copy)
        assertEquals(1280, copy.mtu)
        assertContains(spec.toString(), "excludeRfc1918=true")
        assertContains(spec.toString(), "routeCidrsV6=[::/0]")
        assertContains(spec.toString(), "excludeSelf=true")
    }

    @Test
    fun `sealed result models expose payloads and equality`() {
        assertEquals(1080, (StartResult.Success(1080) as StartResult.Success).socksPort)
        assertEquals("boom", (StartResult.Failure("boom") as StartResult.Failure).reason)
        assertEquals(1081, (ChainResult.Success(1081) as ChainResult.Success).finalSocksPort)
        assertEquals(2, (ChainResult.Failure(2, "failed", 1) as ChainResult.Failure).failedAtIndex)
        assertEquals(42L, (ProbeResult.Success(42L) as ProbeResult.Success).latencyMs)
        assertEquals("timeout", (ProbeResult.Failure("timeout") as ProbeResult.Failure).reason)
        assertEquals(ExitNodeStrategy.ViaSocks("127.0.0.1", 1080), ExitNodeStrategy.ViaSocks("127.0.0.1", 1080))
        assertEquals(IpProbeRoute.Socks("127.0.0.1", 1080), IpProbeRoute.Socks("127.0.0.1", 1080))
        assertTrue(ExitNodeStrategy.AutoSelected().label.isNotBlank())
        assertNotNull(IpProbeRoute.Unavailable("offline").reason)
        assertNotNull(ExitNodeStrategy.Unavailable("offline").reason)
    }

    @Test
    fun `engine config models expose ids and redact secrets`() {
        val singbox = EngineConfig.Singbox(
            beanBlob = byteArrayOf(1, 2),
            protocolType = 3,
            autoSelectBeanBlobs = listOf(byteArrayOf(4)),
            chainBeanBlobs = listOf(byteArrayOf(5)),
            wireGuardConfig = WireGuardOutboundConfig(
                privateKey = "private",
                peerPublicKey = "peer",
                serverHost = "host",
                serverPort = 443,
                localAddresses = listOf("172.16.0.2/32"),
            ),
            proxyMode = true,
        )
        val sameSingbox = singbox.copy(
            beanBlob = byteArrayOf(1, 2),
            autoSelectBeanBlobs = listOf(byteArrayOf(4)),
            chainBeanBlobs = listOf(byteArrayOf(5)),
        )

        assertEquals(EngineId.BYEDPI, EngineConfig.ByeDpi().engineId)
        assertEquals(EngineId.WARP, EngineConfig.WarpProxy().engineId)
        assertEquals(EngineId.FPTN, EngineConfig.Fptn(token = "secret").engineId)
        assertEquals(EngineId.URNETWORK, EngineConfig.Urnetwork(jwtToken = "jwt").engineId)
        assertEquals(EngineId.SINGBOX, singbox.engineId)
        assertEquals(singbox, sameSingbox)
        assertEquals(singbox.hashCode(), sameSingbox.hashCode())
        assertContains(EngineConfig.Urnetwork(jwtToken = "jwt").toString(), "jwtToken=***")
        assertContains(EngineConfig.MasterDns("secret", listOf("1.1.1.1")).toString(), "configToml=***")
        assertContains(EngineConfig.Fptn(token = "secret").toString(), "token=***")
        assertContains(singbox.toString(), "blobSize=2")
        assertContains(singbox.toString(), "proxyMode=true")
    }
}
