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
}
