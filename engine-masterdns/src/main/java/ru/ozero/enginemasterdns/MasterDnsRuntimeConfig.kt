package ru.ozero.enginemasterdns

data class MasterDnsRuntimeConfig(
    val configToml: String,
    val resolvers: List<String>,
    val socksPort: Int,
    val readinessHost: String = DEFAULT_READINESS_HOST,
    val readinessPort: Int = DEFAULT_READINESS_PORT,
    val readinessTimeoutMs: Long = DEFAULT_READINESS_TIMEOUT_MS,
    val readinessPollIntervalMs: Long = DEFAULT_READINESS_POLL_INTERVAL_MS,
    val readinessConnectTimeoutMs: Int = DEFAULT_READINESS_CONNECT_TIMEOUT_MS,
) {
    companion object {
        const val DEFAULT_READINESS_HOST = "1.1.1.1"
        const val DEFAULT_READINESS_PORT = 443
        const val DEFAULT_READINESS_TIMEOUT_MS = 9_000L
        const val DEFAULT_READINESS_POLL_INTERVAL_MS = 250L
        const val DEFAULT_READINESS_CONNECT_TIMEOUT_MS = 3_000
    }
}

data class MasterDnsReadinessConfig(
    val host: String,
    val port: Int,
    val timeoutMs: Long,
    val pollIntervalMs: Long,
    val connectTimeoutMs: Int,
)
