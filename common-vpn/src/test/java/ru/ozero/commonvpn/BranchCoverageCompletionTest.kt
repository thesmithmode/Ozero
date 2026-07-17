package ru.ozero.commonvpn

import org.junit.jupiter.api.Test
import ru.ozero.enginescore.EngineId
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BranchCoverageCompletionTest {

    @Test
    fun `transition matrix accepts only documented state changes`() {
        val controller = TunnelController()
        val method = TunnelController::class.java.getDeclaredMethod(
            "isAllowed",
            TunnelState::class.java,
            TunnelState::class.java,
        ).apply { isAccessible = true }
        val states = listOf(
            TunnelState.Idle,
            TunnelState.Probing(EngineId.BYEDPI),
            TunnelState.Connecting(EngineId.BYEDPI),
            TunnelState.Connected(EngineId.BYEDPI, 1080),
            TunnelState.Disconnecting,
            TunnelState.Failed(EngineId.BYEDPI, "failure"),
        )
        val allowed = setOf(
            TunnelState.Idle::class to TunnelState.Probing::class,
            TunnelState.Probing::class to TunnelState.Connecting::class,
            TunnelState.Probing::class to TunnelState.Failed::class,
            TunnelState.Probing::class to TunnelState.Disconnecting::class,
            TunnelState.Connecting::class to TunnelState.Connected::class,
            TunnelState.Connecting::class to TunnelState.Failed::class,
            TunnelState.Connecting::class to TunnelState.Disconnecting::class,
            TunnelState.Connected::class to TunnelState.Disconnecting::class,
            TunnelState.Connected::class to TunnelState.Failed::class,
            TunnelState.Disconnecting::class to TunnelState.Idle::class,
            TunnelState.Disconnecting::class to TunnelState.Failed::class,
            TunnelState.Failed::class to TunnelState.Probing::class,
            TunnelState.Failed::class to TunnelState.Idle::class,
            TunnelState.Failed::class to TunnelState.Disconnecting::class,
        )

        states.forEach { from ->
            states.forEach { to ->
                val actual = method.invoke(controller, from, to) as Boolean
                if (from::class to to::class in allowed) assertTrue(actual) else assertFalse(actual)
            }
        }
    }

    @Test
    fun `stats rebase detects either counter moving backwards`() {
        val baseline = TunnelStatsReadResult(100, 200, "tun0")

        assertFalse(TunnelStatsReadResult(100, 200, "tun0").shouldRebaseFrom(baseline, null))
        assertTrue(TunnelStatsReadResult(99, 200, "tun0").shouldRebaseFrom(baseline, baseline))
        assertTrue(TunnelStatsReadResult(100, 199, "tun0").shouldRebaseFrom(baseline, baseline))
        assertFalse(TunnelStatsReadResult(101, 201, "tun0").shouldRebaseFrom(baseline, baseline))
    }
}
