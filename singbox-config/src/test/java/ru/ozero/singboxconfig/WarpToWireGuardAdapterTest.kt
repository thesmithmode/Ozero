package ru.ozero.singboxconfig

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals

class WarpToWireGuardAdapterTest {

    @Test
    fun `converts standard WARP config`() {
        val result = WarpToWireGuardAdapter.convert(
            privateKey = "privkey=",
            peerPublicKey = "pubkey=",
            peerEndpoint = "engage.cloudflareclient.com:2408",
            interfaceAddressV4 = "172.16.0.2",
            interfaceAddressV6 = "fd01:db8::1",
            mtu = 1280,
            keepaliveSeconds = 25,
        )

        assertEquals("privkey=", result.privateKey)
        assertEquals("pubkey=", result.peerPublicKey)
        assertEquals("engage.cloudflareclient.com", result.serverHost)
        assertEquals(2408, result.serverPort)
        assertEquals(listOf("172.16.0.2/32", "fd01:db8::1/128"), result.localAddresses)
        assertEquals(1280, result.mtu)
        assertEquals(25, result.keepaliveSeconds)
    }

    @Test
    fun `preserves CIDR if already present`() {
        val result = WarpToWireGuardAdapter.convert(
            privateKey = "k=",
            peerPublicKey = "p=",
            peerEndpoint = "1.2.3.4:51820",
            interfaceAddressV4 = "10.0.0.1/24",
            interfaceAddressV6 = "",
            mtu = 1400,
            keepaliveSeconds = 0,
        )

        assertEquals(listOf("10.0.0.1/24"), result.localAddresses)
        assertEquals("1.2.3.4", result.serverHost)
        assertEquals(51820, result.serverPort)
    }

    @Test
    fun `clamps MTU to valid range`() {
        val result = WarpToWireGuardAdapter.convert(
            privateKey = "k=",
            peerPublicKey = "p=",
            peerEndpoint = "h:1234",
            interfaceAddressV4 = "10.0.0.1",
            interfaceAddressV6 = "",
            mtu = 9000,
            keepaliveSeconds = 0,
        )
        assertEquals(1500, result.mtu)
    }

    @Test
    fun `clamps low MTU and accepts empty interface addresses`() {
        val result = WarpToWireGuardAdapter.convert(
            privateKey = "k=",
            peerPublicKey = "p=",
            peerEndpoint = "h:1234",
            interfaceAddressV4 = "",
            interfaceAddressV6 = "",
            mtu = 1000,
            keepaliveSeconds = 0,
        )

        assertEquals(1280, result.mtu)
        assertEquals(emptyList(), result.localAddresses)
    }

    @Test
    fun `parses IPv6 bracket endpoint`() {
        val (host, port) = WarpToWireGuardAdapter.splitEndpoint("[::1]:8080")
        assertEquals("::1", host)
        assertEquals(8080, port)
    }

    @Test
    fun `parses standard endpoint`() {
        val (host, port) = WarpToWireGuardAdapter.splitEndpoint("example.com:443")
        assertEquals("example.com", host)
        assertEquals(443, port)
    }

    @Test
    fun `throws on invalid endpoint`() {
        assertThrows<IllegalArgumentException> {
            WarpToWireGuardAdapter.splitEndpoint("noport")
        }
    }

    @Test
    fun `throws on invalid endpoint port`() {
        assertThrows<NumberFormatException> {
            WarpToWireGuardAdapter.splitEndpoint("[::1]:bad")
        }
    }
}
