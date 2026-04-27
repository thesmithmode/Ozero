package ru.ozero.enginexray.config

import org.junit.jupiter.api.Test
import ru.ozero.coresubscriptions.uri.Hysteria2Server
import ru.ozero.coresubscriptions.uri.ShadowsocksServer
import ru.ozero.coresubscriptions.uri.TrojanServer
import ru.ozero.coresubscriptions.uri.VlessServer
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class XrayConfigBuilderTest {

    private val builder = XrayConfigBuilder()

    
    @Test
    fun socksInboundOnLocalhost() {
        val cfg = builder.build(sampleVless(), socksPort = 10808)
        assertTrue(cfg.contains(""""listen":"127.0.0.1""""), cfg)
        assertTrue(cfg.contains(""""port":10808"""), cfg)
        assertTrue(cfg.contains(""""protocol":"socks""""))
        assertTrue(cfg.contains(""""udp":true"""))
    }

    @Test
    fun socksPortOutOfRangeRejected() {
        val ex = runCatching { builder.build(sampleVless(), 0) }.exceptionOrNull()
        assertTrue(ex is IllegalArgumentException)
    }

    
    @Test
    fun vlessRealityXhttpCanonical() {
        val s = VlessServer(
            uuid = "00000000-0000-0000-0000-000000000001",
            host = "example.com",
            port = 443,
            encryption = "none",
            security = "reality",
            fingerprint = "chrome",
            publicKey = "PBK",
            shortId = "SID",
            sni = "www.cloudflare.com",
            transport = "xhttp",
            path = "/xhttp-path",
            flow = "xtls-rprx-vision",
        )
        val cfg = builder.build(s, socksPort = 10808)

        assertTrue(cfg.contains(""""protocol":"vless""""))
        assertTrue(cfg.contains(""""id":"00000000-0000-0000-0000-000000000001""""))
        assertTrue(cfg.contains(""""flow":"xtls-rprx-vision""""))
        assertTrue(cfg.contains(""""network":"xhttp""""))
        assertTrue(cfg.contains(""""security":"reality""""))
        assertTrue(cfg.contains(""""publicKey":"PBK""""))
        assertTrue(cfg.contains(""""shortId":"SID""""))
        assertTrue(cfg.contains(""""serverName":"www.cloudflare.com""""))
        assertTrue(cfg.contains(""""fingerprint":"chrome""""))
        assertTrue(cfg.contains(""""path":"/xhttp-path""""))
    }

    @Test
    fun vlessRealityGrpcUsesGrpcSettings() {
        val s = sampleVless().copy(transport = "grpc", path = "my-service")
        val cfg = builder.build(s, 10808)
        assertTrue(cfg.contains(""""network":"grpc""""))
        assertTrue(cfg.contains(""""grpcSettings":{"serviceName":"my-service""""))
        assertFalse(cfg.contains("xhttpSettings"))
    }

    @Test
    fun vlessWsUsesWsSettings() {
        val s = sampleVless().copy(transport = "ws", path = "/ws", security = "tls")
        val cfg = builder.build(s, 10808)
        assertTrue(cfg.contains(""""network":"ws""""))
        assertTrue(cfg.contains(""""wsSettings":{"path":"/ws""""))
        assertTrue(cfg.contains(""""tlsSettings""""))
        assertFalse(cfg.contains("realitySettings"))
    }

    @Test
    fun vlessRealitySniFallsBackToHost() {
        val s = sampleVless().copy(sni = null)
        val cfg = builder.build(s, 10808)
        assertTrue(cfg.contains(""""serverName":"example.com""""))
    }

    @Test
    fun vlessFlowOmittedWhenBlank() {
        val s = sampleVless().copy(flow = null)
        val cfg = builder.build(s, 10808)
        assertFalse(cfg.contains("\"flow\""))
    }

    @Test
    fun vlessBlankHostRejected() {
        val ex = runCatching { builder.build(sampleVless().copy(host = ""), 10808) }.exceptionOrNull()
        assertTrue(ex is IllegalArgumentException)
    }

    @Test
    fun vlessBlankUuidRejected() {
        val ex = runCatching { builder.build(sampleVless().copy(uuid = ""), 10808) }.exceptionOrNull()
        assertTrue(ex is IllegalArgumentException)
    }

    
    @Test
    fun hysteria2BasicConfig() {
        val s = Hysteria2Server(
            password = "pass",
            host = "hy.example.com",
            port = 443,
            sni = "hy.example.com",
        )
        val cfg = builder.build(s, 10808)
        assertTrue(cfg.contains(""""protocol":"hysteria2""""))
        assertTrue(cfg.contains(""""address":"hy.example.com""""))
        assertTrue(cfg.contains(""""port":443"""))
        assertTrue(cfg.contains(""""password":"pass""""))
        assertTrue(cfg.contains(""""network":"udp""""))
    }

    @Test
    fun hysteria2ObfsIncluded() {
        val s = Hysteria2Server(
            password = "pass",
            host = "hy.example.com",
            port = 443,
            obfs = "salamander",
            obfsPassword = "secret",
        )
        val cfg = builder.build(s, 10808)
        assertTrue(cfg.contains(""""obfs":{"type":"salamander","password":"secret"}"""))
    }

    @Test
    fun hysteria2InsecurePropagated() {
        val s = Hysteria2Server("p", "h.example", 443, insecure = true)
        val cfg = builder.build(s, 10808)
        assertTrue(cfg.contains(""""allowInsecure":true"""))
    }

    
    @Test
    fun trojanConfig() {
        val s = TrojanServer(
            password = "trpass",
            host = "tr.example.com",
            port = 443,
            sni = "tr.example.com",
        )
        val cfg = builder.build(s, 10808)
        assertTrue(cfg.contains(""""protocol":"trojan""""))
        assertTrue(cfg.contains(""""password":"trpass""""))
        assertTrue(cfg.contains(""""serverName":"tr.example.com""""))
        assertTrue(cfg.contains(""""allowInsecure":false"""))
    }

    @Test
    fun trojanSniFallbackOrder() {
        val s = TrojanServer("p", "host.local", 443, sni = null, peer = "peer.local")
        val cfg = builder.build(s, 10808)
        assertTrue(cfg.contains(""""serverName":"peer.local""""))
    }

    
    @Test
    fun shadowsocksConfig() {
        val s = ShadowsocksServer(
            method = "chacha20-ietf-poly1305",
            password = "sspass",
            host = "ss.example.com",
            port = 8388,
        )
        val cfg = builder.build(s, 10808)
        assertTrue(cfg.contains(""""protocol":"shadowsocks""""))
        assertTrue(cfg.contains(""""method":"chacha20-ietf-poly1305""""))
        assertTrue(cfg.contains(""""password":"sspass""""))
        assertTrue(cfg.contains(""""address":"ss.example.com""""))
        assertTrue(cfg.contains(""""port":8388"""))
    }

    @Test
    fun shadowsocksBlankMethodRejected() {
        val ex = runCatching {
            builder.build(ShadowsocksServer("", "p", "h", 1), 10808)
        }.exceptionOrNull()
        assertTrue(ex is IllegalArgumentException)
    }

    
    @Test
    fun vlessRealityGrpcExactJson() {
        val s = VlessServer(
            uuid = "u",
            host = "h",
            port = 443,
            encryption = "none",
            security = "reality",
            fingerprint = "chrome",
            publicKey = "pbk",
            shortId = "sid",
            sni = "sni",
            transport = "grpc",
            path = "svc",
            flow = null,
        )
        val expected =
            """{"log":{"loglevel":"warning"},""" +
                """"inbounds":[{"tag":"socks-in","port":1080,"listen":"127.0.0.1",""" +
                """"protocol":"socks","settings":{"auth":"noauth","udp":true}}],""" +
                """"outbounds":[{"tag":"proxy","protocol":"vless",""" +
                """"settings":{"vnext":[{"address":"h","port":443,""" +
                """"users":[{"id":"u","encryption":"none"}]}]},""" +
                """"streamSettings":{"network":"grpc","security":"reality",""" +
                """"realitySettings":{"show":false,"fingerprint":"chrome",""" +
                """"serverName":"sni","publicKey":"pbk","shortId":"sid","spiderX":""},""" +
                """"grpcSettings":{"serviceName":"svc","multiMode":false}}}]}"""
        assertEquals(expected, builder.build(s, 1080))
    }

    
    @Test
    fun chainHasTwoOutboundsWithCorrectTags() {
        val entry = sampleVless().copy(host = "ru-entry.example.com", port = 443)
        val exit = sampleVless().copy(host = "nl-exit.example.com", port = 8443, uuid = "exit-uuid")
        val cfg = builder.buildChain(entry, exit, socksPort = 10808)
        assertTrue(cfg.contains(""""tag":"proxy""""))
        assertTrue(cfg.contains(""""tag":"exit-proxy""""))
                assertTrue(cfg.contains(""""proxySettings":{"tag":"exit-proxy"}"""), cfg)
    }

    @Test
    fun chainEntryReferencesExitTag() {
        val entry = sampleVless().copy(host = "ru1.com")
        val exit = sampleVless().copy(host = "nl1.com", uuid = "EXIT")
        val cfg = builder.buildChain(entry, exit, 10808)
                val iEntry = cfg.indexOf(""""tag":"proxy"""")
        val iExit = cfg.indexOf(""""tag":"exit-proxy"""")
        assertTrue(iEntry > 0 && iExit > iEntry, cfg)
    }

    @Test
    fun chainExitOutboundCarriesExitParams() {
        val entry = sampleVless().copy(host = "ru.com", uuid = "ENTRY")
        val exit = sampleVless().copy(host = "nl.com", uuid = "EXIT", publicKey = "EXITPK")
        val cfg = builder.buildChain(entry, exit, 10808)
        assertTrue(cfg.contains(""""id":"EXIT""""))
        assertTrue(cfg.contains(""""publicKey":"EXITPK""""))
    }

    @Test
    fun chainRejectsSameServer() {
        val s = sampleVless()
        val ex = runCatching { builder.buildChain(s, s, 10808) }.exceptionOrNull()
        assertTrue(ex is IllegalArgumentException)
    }

    @Test
    fun chainRejectsBlankHosts() {
        val s = sampleVless()
        val ex1 = runCatching { builder.buildChain(s.copy(host = ""), s.copy(host = "x"), 10808) }.exceptionOrNull()
        val ex2 = runCatching { builder.buildChain(s.copy(host = "x"), s.copy(host = ""), 10808) }.exceptionOrNull()
        assertTrue(ex1 is IllegalArgumentException)
        assertTrue(ex2 is IllegalArgumentException)
    }

    @Test
    fun chainRejectsInvalidPort() {
        val s = sampleVless()
        val ex = runCatching {
            builder.buildChain(s.copy(host = "a"), s.copy(host = "b"), 0)
        }.exceptionOrNull()
        assertTrue(ex is IllegalArgumentException)
    }

    private fun sampleVless() = VlessServer(
        uuid = "uuid",
        host = "example.com",
        port = 443,
        encryption = "none",
        security = "reality",
        fingerprint = "chrome",
        publicKey = "PBK",
        shortId = "SID",
        sni = "sni.example.com",
        transport = "xhttp",
        path = "/p",
        flow = "xtls-rprx-vision",
    )
}
