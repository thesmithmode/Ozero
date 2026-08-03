package ru.ozero.singboxconfig

import org.junit.jupiter.api.Test
import ru.ozero.singboxfmt.BeanBlobSchema
import ru.ozero.singboxfmt.DecodedCandidate
import ru.ozero.singboxfmt.VLESSBean
import kotlin.test.assertEquals
import kotlin.test.assertIs

class PersistedProfileRecoveryTest {
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

        assertEquals(RecoveryResult.MigrationAmbiguous, recovered)
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
}
