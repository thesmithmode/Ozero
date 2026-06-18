package ru.ozero.app.ui.logs

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.ozero.app.logging.LogEntry
import ru.ozero.app.logging.LogLevel
import ru.ozero.app.ui.theme.OzeroTheme
import java.io.File

@RunWith(AndroidJUnit4::class)
class LogsScreenContentTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun rendersFilteredRowsAndReportsFilterCallbacks() {
        val visible = LogEntry(1_000L, LogLevel.ERROR, "Vpn", 1, "visible failure")
        val hidden = LogEntry(2_000L, LogLevel.INFO, "Ui", 1, "hidden info")
        val events = mutableListOf<String>()

        render(
            state = LogsUiState(
                entries = listOf(visible, hidden),
                tagFilter = "Vpn",
                levelFilter = "WARN",
            ),
            onTagFilter = { events += "tag:$it" },
            onLevelFilter = { events += "level:$it" },
        )

        composeRule.onNodeWithTag(LogsScreenTestTags.logRow(visible)).assertIsDisplayed()
        composeRule.onNodeWithTag(LogsScreenTestTags.filterChip(FILTER_ALL)).performClick()
        composeRule.onNodeWithTag(LogsScreenTestTags.filterChip("ERROR")).performClick()

        assertEquals(listOf("tag:$FILTER_ALL", "level:ERROR"), events)
    }

    @Test
    fun backAndClearActionsInvokeCallbacks() {
        val events = mutableListOf<String>()

        render(
            state = LogsUiState(),
            onBack = { events += "back" },
            onClear = { events += "clear" },
        )

        composeRule.onNodeWithTag(LogsScreenTestTags.CLEAR_TOP).performClick()
        composeRule.onNodeWithTag(LogsScreenTestTags.CLEAR_FOOTER).performClick()
        composeRule.onNodeWithTag(LogsScreenTestTags.BACK).performClick()

        assertEquals(listOf("clear", "clear", "back"), events)
    }

    private fun render(
        state: LogsUiState,
        onBack: () -> Unit = {},
        onClear: () -> Unit = {},
        onCopyAll: () -> String = { "" },
        onCopyFiltered: (LogLevel) -> String = { "" },
        onCreateFilteredFile: (LogLevel, (File?) -> Unit) -> Unit = { _, _ -> },
        onTagFilter: (String) -> Unit = {},
        onLevelFilter: (String) -> Unit = {},
    ) {
        composeRule.setContent {
            OzeroTheme {
                LogsScreenContent(
                    state = state,
                    onBack = onBack,
                    onClear = onClear,
                    onCopyAll = onCopyAll,
                    onCopyFiltered = onCopyFiltered,
                    onCreateFilteredFile = onCreateFilteredFile,
                    onTagFilter = onTagFilter,
                    onLevelFilter = onLevelFilter,
                )
            }
        }
    }
}
