package ru.ozero.app.ui.diag

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.ozero.app.ui.theme.OzeroTheme

@RunWith(AndroidJUnit4::class)
class DiagnosticsScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun notConnectedRendersHint() {
        render(DiagnosticsUiState.NotConnected)
        composeRule.onNodeWithTag(DiagnosticsTestTags.NOT_CONNECTED).assertIsDisplayed()
    }

    @Test
    fun idleShowsRunButton() {
        render(DiagnosticsUiState.Idle)
        composeRule.onNodeWithTag(DiagnosticsTestTags.IDLE).assertIsDisplayed()
        composeRule.onNodeWithTag(DiagnosticsTestTags.RUN_BUTTON).assertIsDisplayed()
    }

    @Test
    fun runButtonInvokesCallback() {
        var ran = false
        composeRule.setContent {
            OzeroTheme {
                DiagnosticsScreenContent(
                    state = DiagnosticsUiState.Idle,
                    onBack = {},
                    onRun = { ran = true },
                    onStop = {},
                )
            }
        }
        composeRule.onNodeWithTag(DiagnosticsTestTags.RUN_BUTTON).performClick()
        assert(ran)
    }

    @Test
    fun runningRendersProgressAndStop() {
        render(DiagnosticsUiState.Running(total = 20, completed = 7))
        composeRule.onNodeWithTag(DiagnosticsTestTags.PROGRESS).assertIsDisplayed()
        composeRule.onNodeWithTag(DiagnosticsTestTags.PROGRESS_LABEL).assertIsDisplayed()
        composeRule.onNodeWithTag(DiagnosticsTestTags.STOP_BUTTON).assertIsDisplayed()
    }

    @Test
    fun stopInvokesCallback() {
        var stopped = false
        composeRule.setContent {
            OzeroTheme {
                DiagnosticsScreenContent(
                    state = DiagnosticsUiState.Running(total = 20, completed = 1),
                    onBack = {},
                    onRun = {},
                    onStop = { stopped = true },
                )
            }
        }
        composeRule.onNodeWithTag(DiagnosticsTestTags.STOP_BUTTON).performClick()
        assert(stopped)
    }

    @Test
    fun doneRendersAllResults() {
        val results =
            listOf(
                DiagResult.Success("https://a.com", 120, 200),
                DiagResult.Failure("https://b.com", "timeout"),
            )
        render(DiagnosticsUiState.Done(results))

        composeRule.onNodeWithTag(DiagnosticsTestTags.RESULTS_LIST).assertIsDisplayed()
        composeRule
            .onNodeWithTag(DiagnosticsTestTags.RESULT_PREFIX + "https://a.com")
            .assertIsDisplayed()
        composeRule
            .onNodeWithTag(DiagnosticsTestTags.RESULT_PREFIX + "https://b.com")
            .assertIsDisplayed()
    }

    @Test
    fun backInvokesCallback() {
        var back = false
        composeRule.setContent {
            OzeroTheme {
                DiagnosticsScreenContent(
                    state = DiagnosticsUiState.Idle,
                    onBack = { back = true },
                    onRun = {},
                    onStop = {},
                )
            }
        }
        composeRule.onNodeWithTag(DiagnosticsTestTags.BACK).performClick()
        assert(back)
    }

    private fun render(state: DiagnosticsUiState) {
        composeRule.setContent {
            OzeroTheme {
                DiagnosticsScreenContent(
                    state = state,
                    onBack = {},
                    onRun = {},
                    onStop = {},
                )
            }
        }
    }
}
