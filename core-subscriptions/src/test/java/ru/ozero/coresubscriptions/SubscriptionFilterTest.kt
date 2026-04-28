package ru.ozero.coresubscriptions

import org.junit.jupiter.api.Test
import ru.ozero.coresubscriptions.uri.Hysteria2Server
import ru.ozero.coresubscriptions.uri.ParsedServer
import ru.ozero.coresubscriptions.uri.ShadowsocksServer
import ru.ozero.coresubscriptions.uri.TrojanServer
import ru.ozero.coresubscriptions.uri.VlessServer
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SubscriptionFilterTest {
    private val filter = SubscriptionFilter()

    @Test
    fun vlessRealityIsLive() {
        val s = vless(security = "reality", transport = "xhttp")
        assertTrue(filter.isSupported(ParsedServer.Vless(s)))
    }

    @Test
    fun vlessWithoutRealityIsDead() {
        val s = vless(security = "none", transport = "tcp")
        assertFalse(filter.isSupported(ParsedServer.Vless(s)))
    }

    @Test
    fun vlessTlsGrpcIsDead() {
        val s = vless(security = "tls", transport = "grpc")
        assertFalse(filter.isSupported(ParsedServer.Vless(s)))
    }

    @Test
    fun hysteria2IsLive() {
        val s = Hysteria2Server(password = "p", host = "h", port = 443)
        assertTrue(filter.isSupported(ParsedServer.Hysteria2(s)))
    }

    @Test
    fun hysteria2InsecureIsRejected() {
        val s = Hysteria2Server(password = "p", host = "h", port = 443, insecure = true)
        assertFalse(filter.isSupported(ParsedServer.Hysteria2(s)))
    }

    @Test
    fun trojanAnyIsLive() {
        val s = TrojanServer(password = "p", host = "h", port = 443)
        assertTrue(filter.isSupported(ParsedServer.Trojan(s)))
    }

    @Test
    fun shadowsocksIsDead() {
        val s = ShadowsocksServer(method = "aes-256-gcm", password = "p", host = "h", port = 8388)
        assertFalse(filter.isSupported(ParsedServer.Shadowsocks(s)))
    }

    @Test
    fun errorIsDead() {
        assertFalse(filter.isSupported(ParsedServer.Error("malformed")))
    }

    private fun vless(security: String, transport: String): VlessServer =
        VlessServer(
            uuid = "UUID",
            host = "host.io",
            port = 443,
            security = security,
            transport = transport,
        )
}
