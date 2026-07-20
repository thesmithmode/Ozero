package ru.ozero.enginescore

sealed class ProbeResult {
    data class Success(val latencyMs: Long) : ProbeResult()
    data class Failure(
        val reason: String,
        val cause: Throwable? = null,
        val code: Code = Code.UNKNOWN,
    ) : ProbeResult() {
        enum class Code {
            UNKNOWN,
            ROUTED_PROBE_FAILED,
        }
    }
}
