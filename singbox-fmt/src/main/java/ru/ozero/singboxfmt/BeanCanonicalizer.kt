package ru.ozero.singboxfmt

sealed interface CanonicalizationResult {
    data class Canonical(val bean: AbstractBean) : CanonicalizationResult
    data class Rejected(val reason: String) : CanonicalizationResult
}

object BeanCanonicalizer {
    fun canonicalizeOrError(bean: AbstractBean): CanonicalizationResult = runCatching {
        val copy = KryoSerializer.copy(bean)
        copy.applyCanonicalDefaults()
        CanonicalizationResult.Canonical(copy)
    }.getOrElse { error -> CanonicalizationResult.Rejected(error.javaClass.simpleName) }

    fun canonicalize(bean: AbstractBean): CanonicalizationResult = canonicalizeOrError(bean)
}

fun AbstractBean.canonicalBeanOrThrow(): AbstractBean =
    when (val result = BeanCanonicalizer.canonicalizeOrError(this)) {
        is CanonicalizationResult.Canonical -> result.bean
        is CanonicalizationResult.Rejected -> throw IllegalArgumentException(
            "Bean canonicalization failed: ${result.reason}",
        )
    }

fun AbstractBean.applyCanonicalDefaults(): AbstractBean {
    if (this is StandardV2RayBean) {
        type = normalizeSingboxTransport(type)
        security = security.trim().lowercase().ifBlank { "none" }
        headerType = headerType.trim().lowercase().ifBlank { "none" }
        packetEncoding = packetEncoding.trim().lowercase().ifBlank { "none" }
        muxPacketEncoding = muxPacketEncoding.trim().lowercase().ifBlank { "none" }
    }
    return this
}

fun normalizeSingboxTransport(raw: String): String = when (val normalized = raw.trim().lowercase()) {
    "", "raw" -> "tcp"
    "websocket" -> "ws"
    "h2" -> "http"
    "xhttp" -> "splithttp"
    else -> normalized
}
