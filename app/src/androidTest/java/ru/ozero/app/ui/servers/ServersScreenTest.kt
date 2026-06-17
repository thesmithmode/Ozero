package ru.ozero.app.ui.servers

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.ozero.app.ui.theme.OzeroTheme
import ru.ozero.corestorage.entity.ServerEntity

@RunWith(AndroidJUnit4::class)
class ServersScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val sample = listOf(
        server("a", "RU"),
        server("b", "DE"),
    )

    @Test
    fun emptyStateRenders() {
        render(ServersUiState.Empty)
        composeRule.onNodeWithTag(ServersTestTags.EMPTY).assertIsDisplayed()
    }

    @Test
    fun loadingShowsProgress() {
        render(ServersUiState.Loading)
        composeRule.onNodeWithTag(ServersTestTags.LOADING).assertIsDisplayed()
    }

    @Test
    fun contentShowsDropdownsAndPreview() {
        render(
            ServersUiState.Content(
                servers = sample,
                entryId = null,
                exitId = null,
            ),
        )
        composeRule.onNodeWithTag(ServersTestTags.ENTRY_DROPDOWN).assertIsDisplayed()
        composeRule.onNodeWithTag(ServersTestTags.EXIT_DROPDOWN).assertIsDisplayed()
        composeRule.onNodeWithTag(ServersTestTags.PREVIEW).assertIsDisplayed()
    }

    @Test
    fun saveDisabledWhenIncomplete() {
        render(
            ServersUiState.Content(
                servers = sample,
                entryId = "a",
                exitId = null,
            ),
        )
        composeRule.onNodeWithTag(ServersTestTags.SAVE).assertIsNotEnabled()
    }

    @Test
    fun saveEnabledWhenPairComplete() {
        render(
            ServersUiState.Content(
                servers = sample,
                entryId = "a",
                exitId = "b",
            ),
        )
        composeRule.onNodeWithTag(ServersTestTags.SAVE).assertIsEnabled()
    }

    @Test
    fun saveButtonInvokesCallback() {
        var saved = false
        composeRule.setContent {
            OzeroTheme {
                ServersScreenContent(
                    state = ServersUiState.Content(
                        servers = sample,
                        entryId = "a",
                        exitId = "b",
                    ),
                    onBack = {},
                    onEntrySelect = {},
                    onExitSelect = {},
                    onSavePair = { saved = true },
                    onClearPair = {},
                )
            }
        }
        composeRule.onNodeWithTag(ServersTestTags.SAVE).performClick()
        assertEquals(true, saved)
    }

    @Test
    fun clearButtonInvokesCallbackWhenPairComplete() {
        var cleared = false
        composeRule.setContent {
            OzeroTheme {
                ServersScreenContent(
                    state = ServersUiState.Content(
                        servers = sample,
                        entryId = "a",
                        exitId = "b",
                    ),
                    onBack = {},
                    onEntrySelect = {},
                    onExitSelect = {},
                    onSavePair = {},
                    onClearPair = { cleared = true },
                )
            }
        }

        composeRule.onNodeWithTag(ServersTestTags.CLEAR)
            .assertIsEnabled()
            .performClick()

        assertEquals(true, cleared)
    }

    @Test
    fun dropdownOptionsInvokeSelectionCallbacks() {
        val selected = mutableListOf<String>()
        composeRule.setContent {
            OzeroTheme {
                ServersScreenContent(
                    state = ServersUiState.Content(
                        servers = sample,
                        entryId = null,
                        exitId = null,
                    ),
                    onBack = {},
                    onEntrySelect = { selected += "entry:$it" },
                    onExitSelect = { selected += "exit:$it" },
                    onSavePair = {},
                    onClearPair = {},
                )
            }
        }

        composeRule.onNodeWithTag(ServersTestTags.ENTRY_DROPDOWN).performClick()
        composeRule.onNodeWithTag(ServersTestTags.ENTRY_OPTION_PREFIX + "a").performClick()
        composeRule.onNodeWithTag(ServersTestTags.EXIT_DROPDOWN).performClick()
        composeRule.onNodeWithTag(ServersTestTags.EXIT_OPTION_PREFIX + "b").performClick()

        assertEquals(listOf("entry:a", "exit:b"), selected)
    }

    @Test
    fun backInvokesCallback() {
        var back = false
        composeRule.setContent {
            OzeroTheme {
                ServersScreenContent(
                    state = ServersUiState.Empty,
                    onBack = { back = true },
                    onEntrySelect = {},
                    onExitSelect = {},
                    onSavePair = {},
                    onClearPair = {},
                )
            }
        }
        composeRule.onNodeWithTag(ServersTestTags.BACK).performClick()
        assertEquals(true, back)
    }

    private fun server(id: String, country: String): ServerEntity =
        ServerEntity(
            id = id,
            country = country,
            role = "entry",
            protocol = "vless",
            uri = "vless://$id",
            port = 443,
        )

    private fun render(state: ServersUiState) {
        composeRule.setContent {
            OzeroTheme {
                ServersScreenContent(
                    state = state,
                    onBack = {},
                    onEntrySelect = {},
                    onExitSelect = {},
                    onSavePair = {},
                    onClearPair = {},
                )
            }
        }
    }
}
