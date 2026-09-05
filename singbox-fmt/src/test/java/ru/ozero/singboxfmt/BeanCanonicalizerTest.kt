package ru.ozero.singboxfmt

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class BeanCanonicalizerTest {

    @Test
    fun `direct VLESS raw transport canonicalizes to tcp`() {
        val bean = VLESSBean().apply { type = "raw" }

        val result = BeanCanonicalizer.canonicalize(bean)

        val canonical = assertIs<CanonicalizationResult.Canonical>(result).bean as VLESSBean
        assertEquals("tcp", canonical.type)
    }

    @Test
    fun `raw transport with whitespace and case canonicalizes to tcp`() {
        val bean = VLESSBean().apply { type = " RAW " }

        val result = BeanCanonicalizer.canonicalize(bean)

        val canonical = assertIs<CanonicalizationResult.Canonical>(result).bean as VLESSBean
        assertEquals("tcp", canonical.type)
    }

    @Test
    fun `websocket transport alias canonicalizes to ws`() {
        val bean = VLESSBean().apply { type = "websocket" }

        val result = BeanCanonicalizer.canonicalize(bean)

        val canonical = assertIs<CanonicalizationResult.Canonical>(result).bean as VLESSBean
        assertEquals("ws", canonical.type)
    }

    @Test
    fun `Kryo deserialize canonicalizes legacy raw transport to tcp`() {
        val blob = KryoSerializer.serialize(VLESSBean().apply { type = "raw" })

        val bean = KryoSerializer.deserialize<VLESSBean>(blob)

        assertEquals("tcp", bean.type)
    }

    @Test
    fun `Kryo deserialize canonicalizes legacy websocket transport to ws`() {
        val blob = KryoSerializer.serialize(VLESSBean().apply { type = "websocket" })

        val bean = KryoSerializer.deserialize<VLESSBean>(blob)

        assertEquals("ws", bean.type)
    }

    @Test
    fun `supported uTLS fingerprints are normalized`() {
        val bean = VLESSBean().apply {
            utlsFingerprint = " Firefox "
            realityFingerprint = " CHROME_PQ "
        }

        val canonical = assertIs<CanonicalizationResult.Canonical>(
            BeanCanonicalizer.canonicalize(bean),
        ).bean as VLESSBean

        assertEquals("firefox", canonical.utlsFingerprint)
        assertEquals("chrome_pq", canonical.realityFingerprint)
    }

    @Test
    fun `unsupported uTLS fingerprints cannot reach sing-box config`() {
        val bean = VLESSBean().apply {
            utlsFingerprint = "AA:BB:CC"
            realityFingerprint = "AA:BB:CC"
        }

        val canonical = assertIs<CanonicalizationResult.Canonical>(
            BeanCanonicalizer.canonicalize(bean),
        ).bean as VLESSBean

        assertEquals("", canonical.utlsFingerprint)
        assertEquals("chrome", canonical.realityFingerprint)
    }
}
