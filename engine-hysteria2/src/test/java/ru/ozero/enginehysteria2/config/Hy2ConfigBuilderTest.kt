package ru.ozero.enginehysteria2.config

import org.junit.jupiter.api.Test
import ru.ozero.coresubscriptions.uri.Hysteria2Server
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class Hy2ConfigBuilderTest {

    private val builder = Hy2ConfigBuilder()

    private fun sampleServer() = Hysteria2Server(
        password = "auth-secret",
        host = "vpn.example.com",
        port = 443,
        sni = "cdn.cloudflare.net",
        insecure = false,
        obfs = "salamander",
        obfsPassword = "obfs-secret",
        remark = "RU-entry-01",
    )

    @Test
    fun minimalConfigContainsServerAndAuth() {
        val cfg = builder.build(sampleServer(), Hy2BuildOptions(socksPort = 10809))
        assertTrue(cfg.contains(""""server":"vpn.example.com:443""""), cfg)
        assertTrue(cfg.contains(""""auth":"auth-secret""""), cfg)
    }

    @Test
    fun socksListensOnLoopback() {
        val cfg = builder.build(sampleServer(), Hy2BuildOptions(socksPort = 10809))
        assertTrue(cfg.contains(""""socks5":{"listen":"127.0.0.1:10809"}"""), cfg)
    }

    @Test
    fun socksPortOutOfRangeRejected() {
        val ex = runCatching {
            builder.build(sampleServer(), Hy2BuildOptions(socksPort = 0))
        }.exceptionOrNull()
        assertTrue(ex is IllegalArgumentException)
    }

    @Test
    fun tlsBlockHasSniAndInsecure() {
        val cfg = builder.build(sampleServer(), Hy2BuildOptions(socksPort = 10809))
        assertTrue(cfg.contains(""""tls":{"""))
        assertTrue(cfg.contains(""""sni":"cdn.cloudflare.net""""))
        assertTrue(cfg.contains(""""insecure":false"""))
    }

    @Test
    fun pinSHA256IncludedWhenProvided() {
        val cfg = builder.build(
            sampleServer(),
            Hy2BuildOptions(socksPort = 10809, pinSHA256 = "AB:CD:EF"),
        )
        assertTrue(cfg.contains(""""pinSHA256":"AB:CD:EF""""))
    }

    @Test
    fun pinSHA256OmittedWhenAbsent() {
        val cfg = builder.build(sampleServer(), Hy2BuildOptions(socksPort = 10809))
        assertFalse(cfg.contains("pinSHA256"))
    }

    @Test
    fun obfsSalamanderRenderedWhenPasswordPresent() {
        val cfg = builder.build(sampleServer(), Hy2BuildOptions(socksPort = 10809))
        assertTrue(cfg.contains(""""obfs":{"type":"salamander","salamander":{"password":"obfs-secret"}}"""), cfg)
    }

    @Test
    fun obfsOmittedWhenPasswordAbsent() {
        val s = sampleServer().copy(obfs = null, obfsPassword = null)
        val cfg = builder.build(s, Hy2BuildOptions(socksPort = 10809))
        assertFalse(cfg.contains(""""obfs""""))
    }

    @Test
    fun transportUdpDefaultHopInterval30s() {
        val cfg = builder.build(sampleServer(), Hy2BuildOptions(socksPort = 10809))
        assertTrue(cfg.contains(""""transport":{"type":"udp","udp":{"hopInterval":"30s"}}"""), cfg)
    }

    @Test
    fun customHopIntervalRendered() {
        val cfg = builder.build(
            sampleServer(),
            Hy2BuildOptions(socksPort = 10809, hopIntervalSeconds = 15),
        )
        assertTrue(cfg.contains(""""hopInterval":"15s""""))
    }

    @Test
    fun portRangeRendersAsHostPortRange() {
        val cfg = builder.build(
            sampleServer(),
            Hy2BuildOptions(socksPort = 10809, portRange = 20000..50000),
        )
        assertTrue(cfg.contains(""""server":"vpn.example.com:20000-50000""""), cfg)
    }

    @Test
    fun bandwidthRenderedWhenProvided() {
        val cfg = builder.build(
            sampleServer(),
            Hy2BuildOptions(socksPort = 10809, bandwidthUp = "100 mbps", bandwidthDown = "500 mbps"),
        )
        assertTrue(cfg.contains(""""bandwidth":{"up":"100 mbps","down":"500 mbps"}"""), cfg)
    }

    @Test
    fun bandwidthOmittedWhenAbsent() {
        val cfg = builder.build(sampleServer(), Hy2BuildOptions(socksPort = 10809))
        assertFalse(cfg.contains(""""bandwidth""""))
    }

    @Test
    fun deterministicKeyOrder() {
        val cfg1 = builder.build(sampleServer(), Hy2BuildOptions(socksPort = 10809))
        val cfg2 = builder.build(sampleServer(), Hy2BuildOptions(socksPort = 10809))
        assertEquals(cfg1, cfg2)
                val iServer = cfg1.indexOf("\"server\"")
        val iAuth = cfg1.indexOf("\"auth\"")
        val iTls = cfg1.indexOf("\"tls\"")
        val iObfs = cfg1.indexOf("\"obfs\"")
        val iSocks = cfg1.indexOf("\"socks5\"")
        val iTransport = cfg1.indexOf("\"transport\"")
        assertTrue(iServer < iAuth, cfg1)
        assertTrue(iAuth < iTls, cfg1)
        assertTrue(iTls < iObfs, cfg1)
        assertTrue(iObfs < iSocks, cfg1)
        assertTrue(iSocks < iTransport, cfg1)
    }

    @Test
    fun insecureTrueWhenServerInsecure() {
        val s = sampleServer().copy(insecure = true)
        val cfg = builder.build(s, Hy2BuildOptions(socksPort = 10809))
        assertTrue(cfg.contains(""""insecure":true"""))
    }

    @Test
    fun missingSniOmitsSniField() {
        val s = sampleServer().copy(sni = null)
        val cfg = builder.build(s, Hy2BuildOptions(socksPort = 10809))
        assertFalse(cfg.contains(""""sni""""))
    }
}
