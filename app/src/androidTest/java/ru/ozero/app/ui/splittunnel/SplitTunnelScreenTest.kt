package ru.ozero.app.ui.splittunnel

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.ozero.app.ui.theme.OzeroTheme
import ru.ozero.commonvpn.split.SplitTunnelMode

@RunWith(AndroidJUnit4::class)
class SplitTunnelScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun loadingShowsProgress() {
        render(SplitTunnelUiState.Loading)
        composeRule.onNodeWithTag(SplitTunnelTestTags.LOADING).assertIsDisplayed()
    }

    @Test
    fun contentShowsAllSegmentsAndApps() {
        render(
            SplitTunnelUiState.Content(
                mode = SplitTunnelMode.ALLOWLIST,
                query = "",
                apps = listOf(
                    AppRow("com.foo", "Foo", isSystem = false, included = true),
                    AppRow("com.bar", "Bar", isSystem = false, included = false),
                ),
            ),
        )

        SplitTunnelMode.entries.forEach { mode ->
            composeRule
                .onNodeWithTag(SplitTunnelTestTags.MODE_SEGMENT_PREFIX + mode.name)
                .assertIsDisplayed()
        }
        composeRule.onNodeWithTag(SplitTunnelTestTags.APP_ROW_PREFIX + "com.foo").assertIsDisplayed()
        composeRule.onNodeWithTag(SplitTunnelTestTags.APP_ROW_PREFIX + "com.bar").assertIsDisplayed()
    }

    @Test
    fun modeSegmentClickInvokesCallback() {
        val captured = mutableListOf<SplitTunnelMode>()
        composeRule.setContent {
            OzeroTheme {
                SplitTunnelScreenContent(
                    state = SplitTunnelUiState.Content(
                        mode = SplitTunnelMode.ALL,
                        query = "",
                        apps = emptyList(),
                    ),
                    onBack = {},
                    onModeChange = { captured += it },
                    onToggleApp = { _, _ -> },
                    onQuery = {},
                )
            }
        }

        composeRule
            .onNodeWithTag(SplitTunnelTestTags.MODE_SEGMENT_PREFIX + SplitTunnelMode.ALLOWLIST.name)
            .performClick()

        assert(captured == listOf(SplitTunnelMode.ALLOWLIST))
    }

    @Test
    fun searchInvokesQueryCallback() {
        val captured = mutableListOf<String>()
        composeRule.setContent {
            OzeroTheme {
                SplitTunnelScreenContent(
                    state = SplitTunnelUiState.Content(
                        mode = SplitTunnelMode.ALL,
                        query = "",
                        apps = emptyList(),
                    ),
                    onBack = {},
                    onModeChange = {},
                    onToggleApp = { _, _ -> },
                    onQuery = { captured += it },
                )
            }
        }

        composeRule.onNodeWithTag(SplitTunnelTestTags.SEARCH).performTextInput("foo")
        assert(captured.joinToString("") == "foo")
    }

    @Test
    fun emptyAppsShowsEmptyMessage() {
        render(
            SplitTunnelUiState.Content(
                mode = SplitTunnelMode.ALL,
                query = "abc",
                apps = emptyList(),
            ),
        )
        composeRule.onNodeWithTag(SplitTunnelTestTags.EMPTY).assertIsDisplayed()
    }

    @Test
    fun backInvokesCallback() {
        var back = false
        composeRule.setContent {
            OzeroTheme {
                SplitTunnelScreenContent(
                    state = SplitTunnelUiState.Loading,
                    onBack = { back = true },
                    onModeChange = {},
                    onToggleApp = { _, _ -> },
                    onQuery = {},
                )
            }
        }
        composeRule.onNodeWithTag(SplitTunnelTestTags.BACK).performClick()
        assert(back)
    }

    private fun render(state: SplitTunnelUiState) {
        composeRule.setContent {
            OzeroTheme {
                SplitTunnelScreenContent(
                    state = state,
                    onBack = {},
                    onModeChange = {},
                    onToggleApp = { _, _ -> },
                    onQuery = {},
                )
            }
        }
    }
}
