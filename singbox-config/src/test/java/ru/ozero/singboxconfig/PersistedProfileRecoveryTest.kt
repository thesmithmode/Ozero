package ru.ozero.singboxconfig

import org.junit.jupiter.api.Test
import ru.ozero.singboxfmt.BeanBlobSchema
import ru.ozero.singboxfmt.DecodedCandidate
import ru.ozero.singboxfmt.KryoSerializer
import ru.ozero.singboxfmt.VLESSBean
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PersistedProfileRecoveryTest {
    @Test
    fun `typed protocol recovery does not depend on numeric ranges`() {
        val bytes = TRANSITIONAL_FIXTURE_HEX.decodeHex()

        val recovered = PersistedProfileRecovery.recover(bytes, PersistedProtocol.VLESS)
        val identity = PersistedProfileRecovery.recoverIdentity(bytes, PersistedProtocol.VLESS.id)

        assertIs<RecoveryResult.Success>(recovered)
        assertNotNull(identity)
        assertTrue(KryoSerializer.decodeCandidates(bytes).any { it.schema == BeanBlobSchema.LEGACY_V1 })
        assertEquals(PersistedProtocol.VLESS, PersistedProtocol.fromId(PersistedProtocol.VLESS.id))
        assertEquals(null, PersistedProtocol.fromId(Int.MAX_VALUE))
    }

    @Test
    fun `semantically identical candidates collapse to one success`() {
        val bean = validVless("198.51.100.20")
        val candidates = listOf(
            candidate(BeanBlobSchema.LEGACY_V1, bean),
            candidate(BeanBlobSchema.CURRENT_RAW_V2, validVless("198.51.100.20")),
        )

        val recovered = PersistedProfileRecovery.recoverCandidates(candidates, 0)

        assertIs<RecoveryResult.Success>(recovered)
    }

    @Test
    fun `different valid candidates are ambiguous`() {
        val candidates = listOf(
            candidate(BeanBlobSchema.LEGACY_V1, validVless("198.51.100.20")),
            candidate(BeanBlobSchema.CURRENT_RAW_V2, validVless("198.51.100.21")),
        )

        val recovered = PersistedProfileRecovery.recoverCandidates(candidates, 0)

        assertEquals(
            RecoveryFailureCategory.MIGRATION_AMBIGUOUS,
            assertIs<RecoveryResult.Failure>(recovered).category,
        )
    }

    @Test
    fun `unsupported candidate fails instead of becoming ambiguous`() {
        val unsupported = validVless("198.51.100.21").apply { realityPublicKey = "invalid" }
        val candidates = listOf(
            candidate(BeanBlobSchema.LEGACY_V1, validVless("198.51.100.20")),
            candidate(BeanBlobSchema.CURRENT_RAW_V2, unsupported),
        )

        val recovered = PersistedProfileRecovery.recoverCandidates(candidates, 0)

        assertIs<RecoveryResult.Success>(recovered)
    }

    @Test
    fun `identity recovery accepts canonical unsupported profile without runtime validation`() {
        val unsupported = validVless("198.51.100.22").apply { type = "splithttp" }
        val bytes = KryoSerializer.serialize(unsupported)

        val runtimeRecovery = PersistedProfileRecovery.recover(bytes, PersistedProtocol.VLESS)
        val identity = PersistedProfileRecovery.recoverIdentity(bytes, PersistedProtocol.VLESS.id)

        assertEquals(
            RecoveryFailureCategory.UNSUPPORTED_PROFILE,
            assertIs<RecoveryResult.Failure>(runtimeRecovery).category,
        )
        assertEquals(unsupported.serverAddress, assertNotNull(identity).serverAddress)
    }

    @Test
    fun `identity recovery rejects ambiguous fully consumed candidates`() {
        val identity = PersistedProfileRecovery.recoverIdentityCandidates(
            candidates = listOf(
                candidate(BeanBlobSchema.LEGACY_V1, validVless("198.51.100.20")),
                candidate(BeanBlobSchema.CURRENT_RAW_V2, validVless("198.51.100.21")),
            ),
            expectedProtocolType = PersistedProtocol.VLESS.id,
        )

        assertNull(identity)
    }

    private fun candidate(schema: BeanBlobSchema, bean: VLESSBean) =
        DecodedCandidate(schema, bean, bytesConsumed = 100, blobSize = 100)

    private fun validVless(server: String) = VLESSBean().apply {
        serverAddress = server
        serverPort = 443
        uuid = "44444444-4444-4444-4444-444444444444"
        security = "reality"
        sni = "reality.example"
        realityPublicKey = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
        realityShortId = "a1b2c3d4"
        flow = "xtls-rprx-vision"
    }

    private companion object {
        const val TRANSITIONAL_FIXTURE_HEX =
            "0b0081818181008178746c732d727072782d766973696fee0081006e6f6ee5810081008100816e6f6ee5818181810010" +
                "6e6f6ee5816e6f6ee581818181816e6f6ee57261f7006368726f6de5ac42424242424242424242424242424242424242" +
                "42424242424242424242424242424242424242424242424162316332643365b47265616c6974f93139382e35312e3130" +
                "302e32b0f60600000000000068326d75f87265616c6974792d76322e6578616d706ce581617574ef7463f081a5343434" +
                "34343434342d343434342d343434342d343434342d34343434343434343434343400"
    }
}

private fun String.decodeHex(): ByteArray = chunked(2).map { it.toInt(16).toByte() }.toByteArray()
