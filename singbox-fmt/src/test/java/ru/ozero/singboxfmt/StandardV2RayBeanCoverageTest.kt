package ru.ozero.singboxfmt

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StandardV2RayBeanCoverageTest {

    @Test
    fun `default bean exposes all transport and tls defaults`() {
        val bean = TestStandardBean()

        assertEquals("", bean.uuid)
        assertEquals("", bean.encryption)
        assertEquals("tcp", bean.type)
        assertEquals("", bean.host)
        assertEquals("", bean.path)
        assertEquals("none", bean.headerType)
        assertEquals("", bean.mKcpSeed)
        assertEquals("none", bean.quicSecurity)
        assertEquals("", bean.quicKey)
        assertEquals("", bean.grpcServiceName)
        assertFalse(bean.grpcServiceNameCompat)
        assertFalse(bean.grpcMultiMode)
        assertEquals(0, bean.maxEarlyData)
        assertEquals("", bean.earlyDataHeaderName)
        assertFalse(bean.wsUseBrowserForwarder)
        assertFalse(bean.shUseBrowserForwarder)
        assertEquals("auto", bean.splithttpMode)
        assertEquals("", bean.splithttpExtra)
        assertEquals("", bean.meekUrl)
        assertEquals("", bean.mekyaKcpSeed)
        assertEquals("none", bean.mekyaKcpHeaderType)
        assertEquals("", bean.mekyaUrl)
        assertEquals("none", bean.security)
        assertEquals("", bean.sni)
        assertEquals("", bean.alpn)
    }

    @Test
    fun `bean stores tls pinning ech mtls reality and hysteria fields`() {
        val bean = TestStandardBean().apply {
            certificates = "cert"
            pinnedPeerCertificateChainSha256 = "chain"
            pinnedPeerCertificatePublicKeySha256 = "key"
            pinnedPeerCertificateSha256 = "cert-sha"
            allowInsecure = true
            utlsFingerprint = "firefox"
            echEnabled = true
            echConfig = "ech"
            mtlsCertificate = "mtls"
            mtlsCertificatePrivateKey = "private"
            realityPublicKey = "public"
            realityShortId = "sid"
            realityFingerprint = "safari"
            realityDisableX25519Mlkem768 = true
            hy2DownMbps = 11
            hy2UpMbps = 22
            hy2Password = "hy2"
        }

        assertEquals("cert", bean.certificates)
        assertEquals("chain", bean.pinnedPeerCertificateChainSha256)
        assertEquals("key", bean.pinnedPeerCertificatePublicKeySha256)
        assertEquals("cert-sha", bean.pinnedPeerCertificateSha256)
        assertTrue(bean.allowInsecure)
        assertEquals("firefox", bean.utlsFingerprint)
        assertTrue(bean.echEnabled)
        assertEquals("ech", bean.echConfig)
        assertEquals("mtls", bean.mtlsCertificate)
        assertEquals("private", bean.mtlsCertificatePrivateKey)
        assertEquals("public", bean.realityPublicKey)
        assertEquals("sid", bean.realityShortId)
        assertEquals("safari", bean.realityFingerprint)
        assertTrue(bean.realityDisableX25519Mlkem768)
        assertEquals(11, bean.hy2DownMbps)
        assertEquals(22, bean.hy2UpMbps)
        assertEquals("hy2", bean.hy2Password)
    }

    @Test
    fun `bean stores packet mux and sing mux fields`() {
        val bean = TestStandardBean().apply {
            packetEncoding = "xudp"
            mux = true
            muxConcurrency = 32
            muxPacketEncoding = "packet"
            singMux = true
            singMuxProtocol = "smux"
            singMuxMaxConnections = 2
            singMuxMinStreams = 3
            singMuxMaxStreams = 4
            singMuxPadding = true
        }

        assertEquals("xudp", bean.packetEncoding)
        assertTrue(bean.mux)
        assertEquals(32, bean.muxConcurrency)
        assertEquals("packet", bean.muxPacketEncoding)
        assertTrue(bean.singMux)
        assertEquals("smux", bean.singMuxProtocol)
        assertEquals(2, bean.singMuxMaxConnections)
        assertEquals(3, bean.singMuxMinStreams)
        assertEquals(4, bean.singMuxMaxStreams)
        assertTrue(bean.singMuxPadding)
    }

    @Test
    fun `initializeDefaultValues keeps inherited display behavior`() {
        val bean = TestStandardBean().apply {
            serverAddress = "example.com"
            serverPort = 443
        }

        bean.initializeDefaultValues()

        assertEquals("example.com:443", bean.displayAddress())
        assertEquals("example.com:443", bean.displayName())
        bean.name = "named"
        assertEquals("named", bean.displayName())
    }

    private class TestStandardBean : StandardV2RayBean()
}
