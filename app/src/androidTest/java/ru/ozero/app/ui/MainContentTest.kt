package ru.ozero.app.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.ozero.app.ui.components.BOTTOM_DOCK_TEST_TAG
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

    @Test
    fun expertContentKeepsDockReachableAtLargeFontScale() {
        renderExpert(
            state = ExpertMainState(
                tunnelState = TunnelState.Connected(EngineId.WARP, socksPort = 0),
                switching = null,
                stats = null,
                speedHistory = emptyList(),
                stagnant = false,
                healthStatus = HealthMonitor.Status.HEALTHY,
                powerState = PowerDiscState.Connected,
                isConnected = true,
                manualEngine = EngineId.WARP,
                engineAutoPriority = listOf(EngineId.WARP, EngineId.BYEDPI),
                urnetworkPeerCount = 0,
                urnetworkPeerSearchSeconds = 0,
                ipInfo = IpInfoState.Idle,
                killswitchActive = false,
            ),
            callbacks = ExpertMainCallbacks(
                onConnectClick = {},
                onManualEngineSelect = {},
                onRefreshIpInfo = {},
                onOpenEngineParams = {},
                onOpenSplitTunnel = {},
                onOpenSettings = {},
            ),
            fontScale = 2f,
        )

        composeRule.onNodeWithTag(BOTTOM_DOCK_TEST_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(BOTTOM_DOCK_TAB_TEST_TAG_PREFIX + "servers").assertIsDisplayed()
        composeRule.onNodeWithTag(BOTTOM_DOCK_TAB_TEST_TAG_PREFIX + "split_tunnel").assertIsDisplayed()
        composeRule.onNodeWithTag(BOTTOM_DOCK_TAB_TEST_TAG_PREFIX + "settings").assertIsDisplayed()
        composeRule.onNodeWithTag(MainScreenTestTags.ENGINE_CHIPS_ROW).assertExists()
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
        fontScale: Float = 1f,
    ) {
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, fontScale)) {
                OzeroTheme {
                    ExpertMainContent(state = state, callbacks = callbacks)
                }
            }
        }
    }
}
