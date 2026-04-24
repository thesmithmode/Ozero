package ru.ozero.coreapi

sealed class ProbeResult {
    data class Success(val latencyMs: Long) : ProbeResult()
    data class Failure(val reason: String) : ProbeResult()
}
