package ru.ozero.singboxconfig

import org.json.JSONObject
import ru.ozero.singboxfmt.AbstractBean
import ru.ozero.singboxfmt.BeanBlobSchema
import ru.ozero.singboxfmt.DecodedCandidate
import ru.ozero.singboxfmt.KryoSerializer
import ru.ozero.singboxfmt.ShadowsocksBean
import ru.ozero.singboxfmt.TrojanBean
import ru.ozero.singboxfmt.VLESSBean
import ru.ozero.singboxfmt.VMessBean

object PersistedProfileRecovery {
    fun recover(bytes: ByteArray, expectedProtocolType: Int): RecoveryResult =
        recoverCandidates(KryoSerializer.decodeCandidates(bytes), expectedProtocolType)

    internal fun recoverCandidates(
        candidates: List<DecodedCandidate>,
        expectedProtocolType: Int,
    ): RecoveryResult {
        val recovered = candidates.mapNotNull { candidate ->
            if (!protocolMatches(candidate.bean, expectedProtocolType)) return@mapNotNull null
            runCatching {
                val canonical = ConfigBuilder.canonicalBean(candidate.bean)
                require(ConfigBuilder.supportDecisionCanonical(canonical) is BeanSupportDecision.Supported)
                val json = ConfigBuilder.buildSingboxConfigFromCanonical(canonical)
                JSONObject(json)
                RecoveredCandidate(candidate, canonical.value, json)
            }.getOrNull()
        }
        if (recovered.isEmpty()) return RecoveryResult.MigrationFailed
        val distinct = recovered.distinctBy { semanticIdentity(it) }
        if (distinct.size > 1) return RecoveryResult.MigrationAmbiguous
        val selected = distinct.single()
        return RecoveryResult.Success(
            bean = selected.bean,
            schema = selected.candidate.schema,
            bytesConsumed = selected.candidate.bytesConsumed,
            blobSize = selected.candidate.blobSize,
            json = selected.json,
        )
    }

    private fun protocolMatches(bean: AbstractBean, expectedProtocolType: Int): Boolean =
        when (expectedProtocolType) {
            0 -> bean is VLESSBean
            1 -> bean is VMessBean
            2 -> bean is TrojanBean
            3 -> bean is ShadowsocksBean
            else -> false
        }

    private fun semanticIdentity(candidate: RecoveredCandidate): String =
        "${candidate.bean::class.java.name}:${candidate.json}"

    private data class RecoveredCandidate(
        val candidate: DecodedCandidate,
        val bean: AbstractBean,
        val json: String,
    )
}

sealed interface RecoveryResult {
    data class Success(
        val bean: AbstractBean,
        val schema: BeanBlobSchema,
        val bytesConsumed: Int,
        val blobSize: Int,
        val json: String,
    ) : RecoveryResult

    data object MigrationFailed : RecoveryResult
    data object MigrationAmbiguous : RecoveryResult
}
