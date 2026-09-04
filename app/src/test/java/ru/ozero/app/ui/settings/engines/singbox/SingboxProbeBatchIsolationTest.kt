package ru.ozero.app.ui.settings.engines.singbox

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import ru.ozero.singboxfmt.VLESSBean
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SingboxProbeBatchIsolationTest {
    @Test
    fun `negative runtime split budget is rejected`() = runTest {
        assertFailsWith<IllegalArgumentException> {
            isolateProbeBatchFailures(targets(1), maxRuntimeSplits = -1) { batch ->
                ProbeBatchAttempt(success(batch))
            }
        }
    }

    @Test
    fun `singleton runtime rejection is terminal without further splitting`() = runTest {
        val target = targets(1)
        var attempts = 0

        val outcomes = isolateProbeBatchFailures(target, maxRuntimeSplits = 1) { batch ->
            attempts++
            ProbeBatchAttempt(
                outcomes = failed(batch),
                splitReason = ProbeBatchSplitReason.RUNTIME_START,
            )
        }

        assertEquals(1, attempts)
        assertIs<SingboxProbeOutcome.Failure>(outcomes.getValue(target.single().profileId))
    }

    @Test
    fun `runtime start rejection isolates one bad profile without failing good siblings`() = runTest {
        val targets = targets(8)
        val badId = 5L
        val attempts = mutableListOf<List<Long>>()

        val outcomes = isolateProbeBatchFailures(targets, maxRuntimeSplits = 8) { batch ->
            attempts += batch.map { it.profileId }
            when {
                batch.any { it.profileId == badId } && batch.size > 1 -> ProbeBatchAttempt(
                    outcomes = failed(batch),
                    splitReason = ProbeBatchSplitReason.RUNTIME_START,
                )
                batch.singleOrNull()?.profileId == badId -> ProbeBatchAttempt(failed(batch))
                else -> ProbeBatchAttempt(success(batch))
            }
        }

        targets.filterNot { it.profileId == badId }.forEach { target ->
            assertIs<SingboxProbeOutcome.Success>(outcomes.getValue(target.profileId))
        }
        assertIs<SingboxProbeOutcome.Failure>(outcomes.getValue(badId))
        assertTrue(attempts.size < targets.size * 2)
    }

    @Test
    fun `systemic runtime start failure is bounded by shared split budget`() = runTest {
        val targets = targets(8)
        var attempts = 0

        val outcomes = isolateProbeBatchFailures(targets, maxRuntimeSplits = 2) { batch ->
            attempts++
            ProbeBatchAttempt(
                outcomes = failed(batch),
                splitReason = ProbeBatchSplitReason.RUNTIME_START,
            )
        }

        assertEquals(5, attempts)
        assertEquals(targets.map { it.profileId }.toSet(), outcomes.keys)
        assertTrue(outcomes.values.all { it is SingboxProbeOutcome.Failure })
    }

    @Test
    fun `config size split remains mandatory when runtime split budget is zero`() = runTest {
        val targets = targets(8)
        val attemptedSizes = mutableListOf<Int>()

        val outcomes = isolateProbeBatchFailures(targets, maxRuntimeSplits = 0) { batch ->
            attemptedSizes += batch.size
            if (batch.size > 2) {
                ProbeBatchAttempt(
                    outcomes = batch.associate { it.profileId to SingboxProbeOutcome.Failure("config too large") },
                    splitReason = ProbeBatchSplitReason.CONFIG_SIZE,
                )
            } else {
                ProbeBatchAttempt(success(batch))
            }
        }

        assertEquals(listOf(8, 4, 2, 2, 4, 2, 2), attemptedSizes)
        assertTrue(outcomes.values.all { it is SingboxProbeOutcome.Success })
    }

    private fun targets(count: Int): List<SingboxProfileProbeTarget> =
        (1L..count.toLong()).map { id -> SingboxProfileProbeTarget(id, VLESSBean()) }

    private fun success(
        targets: List<SingboxProfileProbeTarget>,
    ): Map<Long, SingboxProbeOutcome> = targets.associate { target ->
        target.profileId to SingboxProbeOutcome.Success(target.profileId.toInt())
    }

    private fun failed(
        targets: List<SingboxProfileProbeTarget>,
    ): Map<Long, SingboxProbeOutcome> = targets.associate { target ->
        target.profileId to SingboxProbeOutcome.Failure(SingboxProbeService.PROBE_ERROR_FAILED)
    }
}
