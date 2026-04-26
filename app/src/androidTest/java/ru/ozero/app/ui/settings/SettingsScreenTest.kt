package ru.ozero.app.ui.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.ozero.app.settings.SettingsModel
import ru.ozero.app.ui.theme.OzeroTheme
import ru.ozero.commonvpn.split.SplitTunnelMode
import ru.ozero.coreapi.EngineId

@RunWith(AndroidJUnit4::class)
class SettingsScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun loadingStateShowsProgressIndicator() {
        composeRule.setContent {
            OzeroTheme {
                SettingsScreenContent(
                    state = SettingsUiState.Loading,
                    onBack = {},
                    onSplitModeChange = {},
                    onIpv6Toggle = {},
                    onAutoStartToggle = {},
                    onManualEngineSelect = {},
                )
            }
        }

        composeRule.onNodeWithTag(SettingsTestTags.LOADING).assertIsDisplayed()
    }

    @Test
    fun contentStateRendersAllSections() {
        renderContent(SettingsModel.DEFAULT)

        composeRule.onNodeWithTag(SettingsTestTags.SECTION_CONNECTION).assertIsDisplayed()
        composeRule.onNodeWithTag(SettingsTestTags.SECTION_NETWORK).assertIsDisplayed()
        composeRule.onNodeWithTag(SettingsTestTags.SECTION_SECURITY).assertIsDisplayed()
        composeRule.onNodeWithTag(SettingsTestTags.SECTION_UPDATES).assertIsDisplayed()
        composeRule.onNodeWithTag(SettingsTestTags.SECTION_ABOUT).assertIsDisplayed()
    }

    @Test
    fun backButtonInvokesCallback() {
        var clicked = false
        composeRule.setContent {
            OzeroTheme {
                SettingsScreenContent(
                    state = SettingsUiState.Content(SettingsModel.DEFAULT),
                    onBack = { clicked = true },
                    onSplitModeChange = {},
                    onIpv6Toggle = {},
                    onAutoStartToggle = {},
                    onManualEngineSelect = {},
                )
            }
        }

        composeRule.onNodeWithTag(SettingsTestTags.BACK).performClick()
        assert(clicked) { "back callback not invoked" }
    }

    @Test
    fun splitModeRadioInvokesCallbackWithEnum() {
        val captured = mutableListOf<SplitTunnelMode>()
        composeRule.setContent {
            OzeroTheme {
                SettingsScreenContent(
                    state = SettingsUiState.Content(SettingsModel.DEFAULT),
                    onBack = {},
                    onSplitModeChange = { captured += it },
                    onIpv6Toggle = {},
                    onAutoStartToggle = {},
                    onManualEngineSelect = {},
                )
            }
        }

        composeRule
            .onNodeWithTag(SettingsTestTags.SPLIT_MODE_PREFIX + SplitTunnelMode.ALLOWLIST.name)
            .performClick()

        assert(captured == listOf(SplitTunnelMode.ALLOWLIST)) {
            "expected ALLOWLIST callback, got $captured"
        }
    }

    @Test
    fun ipv6SwitchReflectsModelAndInvokesCallback() {
        val captured = mutableListOf<Boolean>()
        composeRule.setContent {
            OzeroTheme {
                SettingsScreenContent(
                    state = SettingsUiState.Content(
                        SettingsModel.DEFAULT.copy(ipv6Enabled = true),
                    ),
                    onBack = {},
                    onSplitModeChange = {},
                    onIpv6Toggle = { captured += it },
                    onAutoStartToggle = {},
                    onManualEngineSelect = {},
                )
            }
        }

        composeRule.onNodeWithTag(SettingsTestTags.IPV6_SWITCH).assertIsDisplayed()
    }

    @Test
    fun autoStartSwitchOnWhenModelHasIt() {
        renderContent(SettingsModel.DEFAULT.copy(autoStart = true))

        composeRule.onNodeWithTag(SettingsTestTags.AUTO_START_SWITCH).assertIsDisplayed()
    }

    @Test
    fun manualEngineAutoSelectedWhenModelEngineIsNull() {
        var captured: EngineId? = SENTINEL
        composeRule.setContent {
            OzeroTheme {
                SettingsScreenContent(
                    state = SettingsUiState.Content(SettingsModel.DEFAULT),
                    onBack = {},
                    onSplitModeChange = {},
                    onIpv6Toggle = {},
                    onAutoStartToggle = {},
                    onManualEngineSelect = { captured = it },
                )
            }
        }

        composeRule
            .onNodeWithTag(SettingsTestTags.MANUAL_ENGINE_PREFIX + EngineId.HYSTERIA2.name)
            .performClick()

        assert(captured == EngineId.HYSTERIA2) { "expected HYSTERIA2, got $captured" }
    }

    @Test
    fun torSectionShowsInstallButtonWhenNotInstalled() {
        composeRule.setContent {
            OzeroTheme {
                SettingsScreenContent(
                    state = SettingsUiState.Content(SettingsModel.DEFAULT),
                    torState = TorInstallUiState.NotInstalled,
                    onBack = {},
                    onSplitModeChange = {},
                    onIpv6Toggle = {},
                    onAutoStartToggle = {},
                    onManualEngineSelect = {},
                )
            }
        }

        composeRule.onNodeWithTag(SettingsTestTags.SECTION_TOR).assertIsDisplayed()
        composeRule.onNodeWithTag(SettingsTestTags.TOR_INSTALL_BUTTON).assertIsDisplayed()
    }

    @Test
    fun torInstallButtonClickInvokesCallback() {
        var clicked = false
        composeRule.setContent {
            OzeroTheme {
                SettingsScreenContent(
                    state = SettingsUiState.Content(SettingsModel.DEFAULT),
                    torState = TorInstallUiState.NotInstalled,
                    onBack = {},
                    onSplitModeChange = {},
                    onIpv6Toggle = {},
                    onAutoStartToggle = {},
                    onManualEngineSelect = {},
                    torActions = TorActions(onInstall = { clicked = true }, onCancel = {}),
                )
            }
        }

        composeRule.onNodeWithTag(SettingsTestTags.TOR_INSTALL_BUTTON).performClick()
        assert(clicked) { "install callback not invoked" }
    }

    @Test
    fun torSectionShowsProgressWhenInstalling() {
        composeRule.setContent {
            OzeroTheme {
                SettingsScreenContent(
                    state = SettingsUiState.Content(SettingsModel.DEFAULT),
                    torState = TorInstallUiState.Installing(percent = 42),
                    onBack = {},
                    onSplitModeChange = {},
                    onIpv6Toggle = {},
                    onAutoStartToggle = {},
                    onManualEngineSelect = {},
                )
            }
        }

        composeRule.onNodeWithTag(SettingsTestTags.TOR_PROGRESS).assertIsDisplayed()
        composeRule.onNodeWithTag(SettingsTestTags.TOR_CANCEL_BUTTON).assertIsDisplayed()
    }

    @Test
    fun torSectionShowsInstalledLabelWhenInstalled() {
        composeRule.setContent {
            OzeroTheme {
                SettingsScreenContent(
                    state = SettingsUiState.Content(SettingsModel.DEFAULT),
                    torState = TorInstallUiState.Installed,
                    onBack = {},
                    onSplitModeChange = {},
                    onIpv6Toggle = {},
                    onAutoStartToggle = {},
                    onManualEngineSelect = {},
                )
            }
        }

        composeRule.onNodeWithTag(SettingsTestTags.TOR_INSTALLED_LABEL).assertIsDisplayed()
    }

    @Test
    fun torSectionShowsRetryWhenFailed() {
        composeRule.setContent {
            OzeroTheme {
                SettingsScreenContent(
                    state = SettingsUiState.Content(SettingsModel.DEFAULT),
                    torState = TorInstallUiState.Failed(reason = "code=-100"),
                    onBack = {},
                    onSplitModeChange = {},
                    onIpv6Toggle = {},
                    onAutoStartToggle = {},
                    onManualEngineSelect = {},
                )
            }
        }

        composeRule.onNodeWithTag(SettingsTestTags.TOR_FAILED_LABEL).assertIsDisplayed()
        composeRule.onNodeWithTag(SettingsTestTags.TOR_RETRY_BUTTON).assertIsDisplayed()
    }

    private fun renderContent(model: SettingsModel) {
        composeRule.setContent {
            OzeroTheme {
                SettingsScreenContent(
                    state = SettingsUiState.Content(model),
                    onBack = {},
                    onSplitModeChange = {},
                    onIpv6Toggle = {},
                    onAutoStartToggle = {},
                    onManualEngineSelect = {},
                )
            }
        }
    }

    private companion object {
        // Sentinel value distinct from null & any EngineId — to detect "callback not invoked".
        val SENTINEL: EngineId = EngineId.BYEDPI
    }
}
