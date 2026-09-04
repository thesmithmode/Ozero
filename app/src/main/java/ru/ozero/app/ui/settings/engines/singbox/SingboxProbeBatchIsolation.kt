package ru.ozero.app.ui.settings.engines.singbox

internal enum class ProbeBatchSplitReason {
    CONFIG_SIZE,
    RUNTIME_START,
}

internal data class ProbeBatchAttempt(
    val outcomes: Map<Long, SingboxProbeOutcome>,
    val splitReason: ProbeBatchSplitReason? = null,
)

internal suspend fun isolateProbeBatchFailures(
    targets: List<SingboxProfileProbeTarget>,
    maxRuntimeSplits: Int,
    attempt: suspend (List<SingboxProfileProbeTarget>) -> ProbeBatchAttempt,
): Map<Long, SingboxProbeOutcome> {
    require(maxRuntimeSplits >= 0) { "maxRuntimeSplits must be non-negative" }
    var runtimeSplitsRemaining = maxRuntimeSplits

    suspend fun isolate(batch: List<SingboxProfileProbeTarget>): Map<Long, SingboxProbeOutcome> {
        val result = attempt(batch)
        val splitReason = result.splitReason ?: return result.outcomes
        if (batch.size <= 1) return result.outcomes
        if (splitReason == ProbeBatchSplitReason.RUNTIME_START) {
            if (runtimeSplitsRemaining <= 0) return result.outcomes
            runtimeSplitsRemaining--
        }
        val midpoint = batch.size / 2
        return isolate(batch.subList(0, midpoint)) + isolate(batch.subList(midpoint, batch.size))
    }

    return isolate(targets)
}
