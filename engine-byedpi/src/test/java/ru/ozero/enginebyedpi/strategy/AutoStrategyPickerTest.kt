package ru.ozero.enginebyedpi.strategy

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import ru.ozero.enginescore.EngineCapabilities
import ru.ozero.enginescore.EngineConfig
import ru.ozero.enginescore.EngineId
import ru.ozero.enginescore.EnginePlugin
import ru.ozero.enginescore.EngineStats
import ru.ozero.enginescore.ProbeResult
import ru.ozero.enginescore.StartResult
import ru.ozero.enginescore.Upstream
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AutoStrategyPickerTest {

    @Test
    fun `StrategyScore successRate zero when totalProbes is zero`() {
        val score = StrategyScore(
            strategy = ByeDpiStrategy("-x"),
            totalProbes = 0,
            successCount = 0,
            avgDurationMs = 0L,
        )
        assertEquals(0.0, score.successRate, "totalProbes=0 → successRate=0.0, не деление на ноль")
    }



    private fun fakeEngine(
        startBehavior: (EngineConfig) -> StartResult = { StartResult.Success(socksPort = 1080) },
    ) = object : EnginePlugin {
        override val id = EngineId.BYEDPI
        override val capabilities = EngineCapabilities(
            supportsTcp = true, supportsUdp = false, supportsDoH = false,
            localOnly = true, requiresServer = false, supportsUpstreamSocks = false,
        )

        override suspend fun start(config: EngineConfig, upstream: Upstream): StartResult =
            startBehavior(config)

        override suspend fun stop() = Unit

        override suspend fun probe(): ProbeResult = ProbeResult.Success(latencyMs = 0L)

        override fun stats() = kotlinx.coroutines.flow.flowOf(EngineStats())
    }

    @Test
    fun `empty strategies returns Failed`() = runTest {
        val picker = AutoStrategyPicker(
            byeDpiEngine = fakeEngine(),
            probeClient = mockk(),
            strategies = emptyList(),
            sites = listOf("youtube.com"),
        )
        val result = picker.pickBest()
        assertIs<PickResult.Failed>(result)
        assertTrue(result.reason.contains("no strategies"))
    }

    @Test
    fun `empty sites returns Failed`() = runTest {
        val picker = AutoStrategyPicker(
            byeDpiEngine = fakeEngine(),
            probeClient = mockk(),
            strategies = listOf(ByeDpiStrategy("-d1 -a1")),
            sites = emptyList(),
        )
        val result = picker.pickBest()
        assertIs<PickResult.Failed>(result)
        assertTrue(result.reason.contains("no test sites"))
    }

    @Test
    fun `все стратегии fail probe — Failed с описанием`() = runTest {
        val probe = mockk<SocksProbeClient>()
        coEvery { probe.probe(any()) } returns ProbeResult(
            site = "youtube.com", success = false, durationMs = 100L,
        )
        val picker = AutoStrategyPicker(
            byeDpiEngine = fakeEngine(),
            probeClient = probe,
            strategies = listOf(ByeDpiStrategy("-d1 -a1"), ByeDpiStrategy("-d2 -a1")),
            sites = listOf("youtube.com"),
            betweenDelayMs = 0L,
        )
        val result = picker.pickBest()
        assertIs<PickResult.Failed>(result)
        assertTrue(result.reason.contains("ни одна стратегия"))
    }

    @Test
    fun `winner = strategy with max successRate`() = runTest {
        val probe = mockk<SocksProbeClient>()
        coEvery { probe.probe("yt.com") } returnsMany listOf(
            ProbeResult("yt.com", success = false, durationMs = 100L),
            ProbeResult("yt.com", success = true, durationMs = 200L),
            ProbeResult("yt.com", success = true, durationMs = 150L),
        )
        coEvery { probe.probe("dc.com") } returnsMany listOf(
            ProbeResult("dc.com", success = false, durationMs = 100L),
            ProbeResult("dc.com", success = false, durationMs = 200L),
            ProbeResult("dc.com", success = true, durationMs = 150L),
        )
        val picker = AutoStrategyPicker(
            byeDpiEngine = fakeEngine(),
            probeClient = probe,
            strategies = listOf(
                ByeDpiStrategy("-bad"),
                ByeDpiStrategy("-medium"),
                ByeDpiStrategy("-good"),
            ),
            sites = listOf("yt.com", "dc.com"),
            betweenDelayMs = 0L,
        )
        val result = picker.pickBest()
        val success = assertIs<PickResult.Success>(result)
        assertEquals(3, success.ranked.size)
        assertEquals("-good", success.winner.strategy.command)
        assertEquals(1.0, success.winner.successRate, 0.01)
    }

    @Test
    fun `engine_start failure — strategy получает score 0`() = runTest {
        val probe = mockk<SocksProbeClient>()
        coEvery { probe.probe(any()) } returns ProbeResult("x", success = true, durationMs = 100L)
        var startCount = 0
        val engine = object : EnginePlugin {
            override val id = EngineId.BYEDPI
            override val capabilities = EngineCapabilities(
                supportsTcp = true, supportsUdp = false, supportsDoH = false,
                localOnly = true, requiresServer = false, supportsUpstreamSocks = false,
            )
            override suspend fun start(config: EngineConfig, upstream: Upstream): StartResult {
                startCount++
                return if (startCount == 1) StartResult.Failure("native died") else StartResult.Success(1080)
            }
            override suspend fun stop() = Unit
            override suspend fun probe(): ProbeResult = ProbeResult.Success(latencyMs = 0L)
            override fun stats() = kotlinx.coroutines.flow.flowOf(EngineStats())
        }
        val picker = AutoStrategyPicker(
            byeDpiEngine = engine,
            probeClient = probe,
            strategies = listOf(ByeDpiStrategy("-fail"), ByeDpiStrategy("-ok")),
            sites = listOf("x"),
            betweenDelayMs = 0L,
        )
        val result = picker.pickBest()
        val success = assertIs<PickResult.Success>(result)
        assertEquals("-ok", success.winner.strategy.command)
        val failedScore = success.ranked.find { it.strategy.command == "-fail" }
        assertEquals(0, failedScore?.successCount, "fail strategy → 0 success")
        assertEquals(0, failedScore?.totalProbes, "fail strategy start → totalProbes=0, не sites.size")
    }

    @Test
    fun `progress callback вызывается на каждую strategy`() = runTest {
        val probe = mockk<SocksProbeClient>()
        coEvery { probe.probe(any()) } returns ProbeResult("x", success = true, durationMs = 100L)
        val captured = mutableListOf<Triple<Int, Int, String?>>()
        val picker = AutoStrategyPicker(
            byeDpiEngine = fakeEngine(),
            probeClient = probe,
            strategies = listOf(ByeDpiStrategy("a"), ByeDpiStrategy("b"), ByeDpiStrategy("c")),
            sites = listOf("x"),
            betweenDelayMs = 0L,
        )
        picker.pickBest { current, total, score ->
            captured += Triple(current, total, score?.strategy?.command)
        }
        assertEquals(3, captured.size)
        assertEquals(Triple(1, 3, "a"), captured[0])
        assertEquals(Triple(2, 3, "b"), captured[1])
        assertEquals(Triple(3, 3, "c"), captured[2])
    }

    @Test
    fun `successRate calculation`() = runTest {
        val probe = mockk<SocksProbeClient>()
        coEvery { probe.probe("a") } returns ProbeResult("a", success = true, durationMs = 100L)
        coEvery { probe.probe("b") } returns ProbeResult("b", success = false, durationMs = 200L)
        val picker = AutoStrategyPicker(
            byeDpiEngine = fakeEngine(),
            probeClient = probe,
            strategies = listOf(ByeDpiStrategy("only")),
            sites = listOf("a", "b"),
            betweenDelayMs = 0L,
        )
        val result = picker.pickBest() as PickResult.Success
        val score = result.winner
        assertEquals(2, score.totalProbes)
        assertEquals(1, score.successCount)
        assertEquals(0.5, score.successRate, 0.01)
        assertEquals(150L, score.avgDurationMs)
    }
}
