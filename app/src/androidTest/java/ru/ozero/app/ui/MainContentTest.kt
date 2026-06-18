package ru.ozero.app.ui

import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.ozero.app.ui.components.BOTTOM_DOCK_TAB_TEST_TAG_PREFIX
import ru.ozero.app.ui.components.POWER_DISC_TEST_TAG
import ru.ozero.app.ui.components.PowerDiscState
import ru.ozero.app.ui.theme.OzeroTheme
import ru.ozero.commonvpn.HealthMonitor
import ru.ozero.commonvpn.TunnelState
import ru.ozero.enginescore.EngineId

@RunWith(AndroidJUnit4::class)
class MainContentTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun simpleContentConnectsAndNavigatesWithoutServerTab() {
        val events = mutableListOf<String>()

        renderSimple(
            state = SimpleMainState(
                tunnelState = TunnelState.Idle,
                switching = null,
                powerState = PowerDiscState.Off,
                isConnected = false,
                manualEngine = null,
                urnetworkPeerCount = 0,
                urnetworkPeerSearchSeconds = 0,
            ),
            callbacks = SimpleMainCallbacks(
                onConnectClick = { events += "connect" },
                onOpenSplitTunnel = { events += "split" },
                onOpenSettings = { events += "settings" },
            ),
        )

        composeRule.onNodeWithTag(POWER_DISC_TEST_TAG).performClick()
        composeRule.onNodeWithTag(BOTTOM_DOCK_TAB_TEST_TAG_PREFIX + "split_tunnel").performClick()
        composeRule.onNodeWithTag(BOTTOM_DOCK_TAB_TEST_TAG_PREFIX + "settings").performClick()
        composeRule.onNodeWithTag(BOTTOM_DOCK_TAB_TEST_TAG_PREFIX + "servers").assertDoesNotExist()

        assertEquals(listOf("connect", "split", "settings"), events)
    }

    @Test
    fun expertContentRoutesDockAndRefreshesIpCard() {
        val events = mutableListOf<String>()

        renderExpert(
            state = ExpertMainState(
                tunnelState = TunnelState.Connected(EngineId.URNETWORK, socksPort = 0),
                switching = null,
                stats = null,
                speedHistory = emptyList(),
                stagnant = true,
                healthStatus = HealthMonitor.Status.DEGRADED,
                powerState = PowerDiscState.Connected,
                isConnected = true,
                manualEngine = EngineId.BYEDPI,
                engineAutoPriority = listOf(EngineId.URNETWORK, EngineId.BYEDPI),
                urnetworkPeerCount = 3,
                urnetworkPeerSearchSeconds = 0,
                ipInfo = IpInfoState.Idle,
                killswitchActive = true,
            ),
            callbacks = ExpertMainCallbacks(
                onConnectClick = { events += "disconnect" },
                onManualEngineSelect = { events += "engine:$it" },
                onRefreshIpInfo = { events += "refresh" },
                onOpenEngineParams = { events += "params:$it" },
                onOpenSplitTunnel = { events += "split" },
                onOpenSettings = { events += "settings" },
            ),
        )

        composeRule.onNodeWithTag(POWER_DISC_TEST_TAG).performClick()
        composeRule.onNodeWithTag(MainScreenTestTags.IP_CARD).performClick()
        composeRule.onNodeWithTag(MainScreenTestTags.KILLSWITCH_BADGE).assertIsDisplayed()
        composeRule.onNodeWithTag(MainScreenTestTags.STAGNATION_BADGE).assertIsDisplayed()
        composeRule.onNodeWithTag(MainScreenTestTags.HEALTH_DEGRADED_BADGE).assertIsDisplayed()
        composeRule.onNodeWithTag(MainScreenTestTags.URNETWORK_PEER_COUNT).assertIsDisplayed()
        composeRule.onNodeWithTag(BOTTOM_DOCK_TAB_TEST_TAG_PREFIX + "servers").performClick()
        composeRule.onNodeWithTag(BOTTOM_DOCK_TAB_TEST_TAG_PREFIX + "split_tunnel").performClick()
        composeRule.onNodeWithTag(BOTTOM_DOCK_TAB_TEST_TAG_PREFIX + "settings").performClick()

        assertEquals(
            listOf("disconnect", "refresh", "params:URNETWORK", "split", "settings"),
            events,
        )
    }

    private fun renderSimple(
        state: SimpleMainState,
        callbacks: SimpleMainCallbacks,
    ) {
        composeRule.setContent {
            OzeroTheme {
                SimpleMainContent(state = state, callbacks = callbacks)
            }
        }
    }

    private fun renderExpert(
        state: ExpertMainState,
        callbacks: ExpertMainCallbacks,
    ) {
        composeRule.setContent {
            OzeroTheme {
                ExpertMainContent(state = state, callbacks = callbacks)
            }
        }
    }
}
