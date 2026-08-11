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
import ru.ozero.singboxfmt.hasRequiredOutboundCredentials

enum class PersistedProtocol(val id: Int, val label: String) {
    VLESS(0, "VLESS"),
    VMESS(1, "VMess"),
    TROJAN(2, "Trojan"),
    SHADOWSOCKS(3, "Shadowsocks"),
    ;

    fun matches(bean: AbstractBean): Boolean = when (this) {
        VLESS -> bean is VLESSBean
        VMESS -> bean is VMessBean
        TROJAN -> bean is TrojanBean
        SHADOWSOCKS -> bean is ShadowsocksBean
    }

    companion object {
        fun fromId(id: Int): PersistedProtocol? = entries.firstOrNull { it.id == id }
    }
}

enum class RecoveryFailureCategory {
    DECODE_FAILED,
    MIGRATION_AMBIGUOUS,
    PROTOCOL_MISMATCH,
    INVALID_REQUIRED_FIELDS,
    UNSUPPORTED_PROFILE,
    CONFIG_GENERATION_FAILED,
    LIBBOX_CONFIG_REJECTED,
}

object PersistedProfileRecovery {
    fun recover(bytes: ByteArray, expectedProtocolType: Int): RecoveryResult {
        val candidates = KryoSerializer.decodeCandidates(bytes)
        if (candidates.isEmpty()) return failure(RecoveryFailureCategory.DECODE_FAILED)
        val protocol = PersistedProtocol.fromId(expectedProtocolType)
            ?: return failure(RecoveryFailureCategory.PROTOCOL_MISMATCH, candidates)
        return recoverCandidates(candidates, protocol)
    }

    fun recover(bytes: ByteArray, expectedProtocol: PersistedProtocol): RecoveryResult =
        recoverCandidates(KryoSerializer.decodeCandidates(bytes), expectedProtocol)

    internal fun recoverCandidates(
        candidates: List<DecodedCandidate>,
        expectedProtocolType: Int,
    ): RecoveryResult {
        if (candidates.isEmpty()) return failure(RecoveryFailureCategory.DECODE_FAILED)
        val protocol = PersistedProtocol.fromId(expectedProtocolType)
            ?: return failure(RecoveryFailureCategory.PROTOCOL_MISMATCH, candidates)
        return recoverCandidates(candidates, protocol)
    }

    private fun recoverCandidates(
        candidates: List<DecodedCandidate>,
        expectedProtocol: PersistedProtocol,
    ): RecoveryResult {
        if (candidates.isEmpty()) return failure(RecoveryFailureCategory.DECODE_FAILED)
        val matching = candidates.filter { expectedProtocol.matches(it.bean) }
        if (matching.isEmpty()) return failure(RecoveryFailureCategory.PROTOCOL_MISMATCH, candidates)
        return recoverMatchingCandidates(matching)
    }

    private fun recoverMatchingCandidates(candidates: List<DecodedCandidate>): RecoveryResult {
        val requiredFieldsValid = candidates.filter { hasValidRequiredFields(it.bean) }
        if (requiredFieldsValid.isEmpty()) return failure(RecoveryFailureCategory.INVALID_REQUIRED_FIELDS, candidates)
        val canonical = requiredFieldsValid.mapNotNull { candidate ->
            runCatching { candidate to ConfigBuilder.canonicalBean(candidate.bean) }.getOrNull()
        }
        val supported = canonical.filter {
            ConfigBuilder.supportDecisionCanonical(it.second) is BeanSupportDecision.Supported
        }
        if (supported.isEmpty()) return failure(RecoveryFailureCategory.UNSUPPORTED_PROFILE, candidates)
        val generated = supported.mapNotNull { (candidate, bean) ->
            runCatching {
                val json = ConfigBuilder.buildSingboxConfigFromCanonical(bean)
                JSONObject(json)
                RecoveredCandidate(candidate, bean.value, json)
            }.getOrNull()
        }
        if (generated.isEmpty()) return failure(RecoveryFailureCategory.CONFIG_GENERATION_FAILED, candidates)
        val distinct = generated.distinctBy { "${it.bean::class.java.name}:${it.json}" }
        if (distinct.size > 1) return failure(RecoveryFailureCategory.MIGRATION_AMBIGUOUS, candidates)
        val selected = distinct.single()
        return RecoveryResult.Success(
            bean = selected.bean,
            schema = selected.candidate.schema,
            bytesConsumed = selected.candidate.bytesConsumed,
            blobSize = selected.candidate.blobSize,
            json = selected.json,
        )
    }

    private fun hasValidRequiredFields(bean: AbstractBean): Boolean =
        bean.serverAddress.isNotBlank() &&
            bean.serverPort in 1..65_535 &&
            bean.hasRequiredOutboundCredentials()

    private fun failure(
        category: RecoveryFailureCategory,
        candidates: List<DecodedCandidate> = emptyList(),
    ) = RecoveryResult.Failure(category, candidates.map { it.schema }.distinct())

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

    data class Failure(
        val category: RecoveryFailureCategory,
        val detectedSchemas: List<BeanBlobSchema>,
    ) : RecoveryResult
}
