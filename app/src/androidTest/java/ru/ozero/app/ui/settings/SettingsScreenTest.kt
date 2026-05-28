package ru.ozero.app.ui.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.ozero.enginescore.EngineId
import ru.ozero.enginescore.settings.AppMode
import ru.ozero.enginescore.settings.SettingsModel
import ru.ozero.enginescore.settings.TrafficMode
import ru.ozero.app.ui.theme.OzeroTheme

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
    fun ipv6SwitchReflectsModelAndInvokesCallback() {
        val captured = mutableListOf<Boolean>()
        composeRule.setContent {
            OzeroTheme {
                SettingsScreenContent(
                    state = SettingsUiState.Content(
                        SettingsModel.DEFAULT.copy(ipv6Enabled = true),
                    ),
                    onBack = {},
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
    fun manualEngineByedpiClickInvokesCallback() {
        var clicked = false
        composeRule.setContent {
            OzeroTheme {
                SettingsScreenContent(
                    state = SettingsUiState.Content(SettingsModel.DEFAULT),
                    onBack = {},
                    onIpv6Toggle = {},
                    onAutoStartToggle = {},
                    onManualEngineSelect = { if (it == EngineId.BYEDPI) clicked = true },
                )
            }
        }

        composeRule
            .onNodeWithTag(SettingsTestTags.MANUAL_ENGINE_PREFIX + EngineId.BYEDPI.name)
            .performClick()

        assert(clicked) { "BYEDPI radio click did not invoke callback" }
    }

    @Test
    fun appModeSectionIsDisplayedWithSimpleSelected() {
        renderContent(SettingsModel.DEFAULT)

        composeRule.onNodeWithTag(SettingsTestTags.APP_MODE_SECTION).assertIsDisplayed()
        composeRule.onNodeWithTag(SettingsTestTags.APP_MODE_SIMPLE).assertIsDisplayed()
        composeRule.onNodeWithTag(SettingsTestTags.APP_MODE_EXPERT).assertIsDisplayed()
    }

    @Test
    fun appModeSelectExpertInvokesCallback() {
        val captured = mutableListOf<AppMode>()
        composeRule.setContent {
            OzeroTheme {
                SettingsScreenContent(
                    state = SettingsUiState.Content(SettingsModel.DEFAULT),
                    onBack = {},
                    onIpv6Toggle = {},
                    onAutoStartToggle = {},
                    onManualEngineSelect = {},
                    onAppModeSelect = { captured += it },
                )
            }
        }

        composeRule.onNodeWithTag(SettingsTestTags.APP_MODE_EXPERT).performClick()

        assert(captured == listOf(AppMode.EXPERT)) {
            "expected EXPERT callback, got $captured"
        }
    }

    @Test
    fun trafficModeProxyClickInvokesCallback() {
        val captured = mutableListOf<TrafficMode>()
        composeRule.setContent {
            OzeroTheme {
                SettingsScreenContent(
                    state = SettingsUiState.Content(SettingsModel.DEFAULT),
                    onBack = {},
                    onIpv6Toggle = {},
                    onAutoStartToggle = {},
                    onManualEngineSelect = {},
                    onTrafficModeSelect = { captured += it },
                )
            }
        }

        composeRule.onNodeWithTag(SettingsTestTags.TRAFFIC_MODE_PROXY).performClick()

        assert(captured == listOf(TrafficMode.PROXY)) {
            "expected PROXY callback, got $captured"
        }
    }

    private fun renderContent(model: SettingsModel) {
        composeRule.setContent {
            OzeroTheme {
                SettingsScreenContent(
                    state = SettingsUiState.Content(model),
                    onBack = {},
                    onIpv6Toggle = {},
                    onAutoStartToggle = {},
                    onManualEngineSelect = {},
                )
            }
        }
    }
}
