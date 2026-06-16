package ru.ozero.commonvpn

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NoOpCollaboratorsCoverageTest {

    @Test
    fun `session stats recorder no-op returns sentinel id and accepts both statuses`() = runTest {
        val recorder = SessionStatsRecorder.NoOp

        assertEquals(-1L, recorder.startSession("BYEDPI", 123L))
        recorder.endSession(
            id = -1L,
            endedAt = 456L,
            rxBytes = 10L,
            txBytes = 20L,
            durationMs = 333L,
            status = SessionStatsRecorder.Status.DISCONNECTED,
        )
        recorder.endSession(
            id = -1L,
            endedAt = 789L,
            rxBytes = 0L,
            txBytes = 0L,
            durationMs = 0L,
            status = SessionStatsRecorder.Status.FAILED,
        )
    }

    @Test
    fun `split tunnel no-op returns empty allowlist and blocklist`() = runTest {
        val provider = SplitTunnelRulesProvider.NoOp

        assertTrue(provider.allowlistPackages().isEmpty())
        assertTrue(provider.blocklistPackages().isEmpty())
    }
}
