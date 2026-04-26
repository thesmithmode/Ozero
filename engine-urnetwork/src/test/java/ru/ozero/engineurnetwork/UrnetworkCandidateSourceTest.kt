package ru.ozero.engineurnetwork

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import ru.ozero.coreapi.EngineConfig
import ru.ozero.coreapi.EngineId
import ru.ozero.coreorchestrator.Candidate
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UrnetworkCandidateSourceTest {

    @Test
    fun emptyWhenDisabled() = runTest {
        val source = UrnetworkCandidateSource(
            enabled = { false },
            jwtToken = { "jwt" },
        )
        assertTrue(source.candidates().isEmpty())
    }

    @Test
    fun emptyWhenJwtNull() = runTest {
        val source = UrnetworkCandidateSource(
            enabled = { true },
            jwtToken = { null },
        )
        assertTrue(source.candidates().isEmpty())
    }

    @Test
    fun emptyWhenJwtBlank() = runTest {
        val source = UrnetworkCandidateSource(
            enabled = { true },
            jwtToken = { "  " },
        )
        assertTrue(source.candidates().isEmpty())
    }

    @Test
    fun returnsSingleCandidateWhenEnabledAndJwtSet() = runTest {
        val source = UrnetworkCandidateSource(
            enabled = { true },
            jwtToken = { "my-jwt" },
        )
        val candidates = source.candidates()
        assertEquals(1, candidates.size)
        val c = candidates[0]
        assertEquals(EngineId.URNETWORK, c.engineId)
        assertEquals(Candidate.PRIORITY_URNETWORK, c.priority)
    }

    @Test
    fun candidateConfigContainsJwt() = runTest {
        val source = UrnetworkCandidateSource(
            enabled = { true },
            jwtToken = { "test-jwt-123" },
        )
        val config = source.candidates()[0].config
        val urConfig = config as EngineConfig.Urnetwork
        assertEquals("test-jwt-123", urConfig.jwtToken)
    }

    @Test
    fun candidateConfigPassesRegion() = runTest {
        val source = UrnetworkCandidateSource(
            enabled = { true },
            jwtToken = { "jwt" },
            region = "eu-central",
        )
        val config = source.candidates()[0].config as EngineConfig.Urnetwork
        assertEquals("eu-central", config.region)
    }

    @Test
    fun urnetworkPriorityBetweenByeDpiAndTor() {
        assertTrue(Candidate.PRIORITY_URNETWORK > Candidate.PRIORITY_TOR)
        assertTrue(Candidate.PRIORITY_URNETWORK < Candidate.PRIORITY_BYEDPI)
    }
}
