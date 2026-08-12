package ru.ozero.app.ui

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import ru.ozero.app.ui.components.BOTTOM_DOCK_TAB_TEST_TAG_PREFIX
import ru.ozero.app.ui.components.PowerDiscState
import ru.ozero.app.ui.onboarding.OnboardingContent
import ru.ozero.app.ui.servers.ServersScreenContent
import ru.ozero.app.ui.servers.ServersTestTags
import ru.ozero.app.ui.servers.ServersUiState
import ru.ozero.app.ui.theme.OzeroTheme
import ru.ozero.commonvpn.HealthMonitor
import ru.ozero.commonvpn.TunnelState
import ru.ozero.corestorage.entity.ServerEntity
import ru.ozero.enginescore.EngineId
import ru.ozero.enginescore.settings.AppMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AdaptiveUiRobolectricTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `expert main keeps traffic and every dock action reachable at large font scale`() {
        composeRule.setContent {
            ScaledViewport {
                ExpertMainContent(
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
                )
            }
        }

        composeRule.onNodeWithTag(MainScreenTestTags.TRAFFIC_STATS).assertIsDisplayed()
        composeRule.onNodeWithTag(BOTTOM_DOCK_TAB_TEST_TAG_PREFIX + "servers").assertIsDisplayed()
        composeRule.onNodeWithTag(BOTTOM_DOCK_TAB_TEST_TAG_PREFIX + "split_tunnel").assertIsDisplayed()
        composeRule.onNodeWithTag(BOTTOM_DOCK_TAB_TEST_TAG_PREFIX + "settings").assertIsDisplayed()
    }

    @Test
    fun `onboarding actions remain reachable at large font scale`() {
        composeRule.setContent {
            ScaledViewport {
                OnboardingContent(
                    pageIndex = 4,
                    currentLocaleTag = null,
                    currentAppMode = AppMode.SIMPLE,
                    onLocaleSelect = {},
                    onAppModeSelect = {},
                    onNext = {},
                    onSkip = {},
                    onFinish = {},
                )
            }
        }

        composeRule.onNodeWithTag("onboarding_skip").assertIsDisplayed()
        composeRule.onNodeWithTag("onboarding_finish").assertIsDisplayed()
    }

    @Test
    fun `server actions remain reachable at large font scale`() {
        composeRule.setContent {
            ScaledViewport {
                ServersScreenContent(
                    state = ServersUiState.Content(
                        servers = listOf(server("entry", "RU"), server("exit", "DE")),
                        entryId = "entry",
                        exitId = "exit",
                    ),
                    onBack = {},
                    onEntrySelect = {},
                    onExitSelect = {},
                    onSavePair = {},
                    onClearPair = {},
                )
            }
        }

        composeRule.onNodeWithTag(ServersTestTags.SAVE).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(ServersTestTags.CLEAR).performScrollTo().assertIsDisplayed()
    }

    @androidx.compose.runtime.Composable
    private fun ScaledViewport(content: @androidx.compose.runtime.Composable () -> Unit) {
        CompositionLocalProvider(LocalDensity provides Density(density = 1f, fontScale = 2f)) {
            OzeroTheme {
                Box(modifier = Modifier.size(width = 320.dp, height = 600.dp)) {
                    content()
                }
            }
        }
    }

    private fun server(id: String, country: String): ServerEntity =
        ServerEntity(
            id = id,
            country = country,
            role = id,
            protocol = "vless",
            uri = "vless://$id",
            port = 443,
        )
}
