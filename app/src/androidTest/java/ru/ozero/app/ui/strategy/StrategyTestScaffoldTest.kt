package ru.ozero.app.ui.strategy

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.ozero.app.ui.theme.OzeroTheme

@RunWith(AndroidJUnit4::class)
class StrategyTestScaffoldTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun idleScaffoldStartsScanAndSwitchesMode() {
        val events = mutableListOf<String>()

        render(
            isRunning = false,
            settings = StrategyTestSettings(evolutionMode = false),
            onRunToggle = { events += "start" },
            onModeChange = { events += "mode:$it" },
        )

        composeRule.onNodeWithTag("strategy_test_start_btn").performClick()
        composeRule.onNodeWithTag("scan_mode_deep").performClick()

        assertEquals(listOf("start", "mode:true"), events)
    }

    @Test
    fun runningScaffoldStopsScanAndShowsProgress() {
        val events = mutableListOf<String>()

        render(
            isRunning = true,
            settings = StrategyTestSettings(evolutionMode = false),
            onRunToggle = { events += "stop" },
        )

        composeRule.onNodeWithTag("strategy_test_running_progress").assertIsDisplayed()
        composeRule.onNodeWithTag("strategy_test_stop_btn").performClick()

        assertEquals(listOf("stop"), events)
    }

    @Test
    fun strategyRowApplyAndSaveInvokeCallbacks() {
        val events = mutableListOf<String>()

        render(
            isRunning = false,
            settings = StrategyTestSettings(evolutionMode = false),
            strategies = listOf(StrategyResult(command = "-Ku -An", totalRequests = 2, isCompleted = true)),
            onApply = { events += "apply:$it" },
            onToggleSave = { events += "save:$it" },
        )

        composeRule.onNodeWithTag("strategy_apply_0").performClick()
        composeRule.onNodeWithTag("strategy_save_0").performClick()

        assertEquals(listOf("apply:-Ku -An", "save:-Ku -An"), events)
    }

    @Test
    fun topBarActionsOpenExpectedSheets() {
        val opened = mutableListOf<SheetTarget>()

        render(
            isRunning = false,
            onShowSheet = { opened += it },
        )

        composeRule.onNodeWithTag("domain_lists_btn").performClick()
        composeRule.onNodeWithTag("saved_strategies_btn").performClick()
        composeRule.onNodeWithTag("strategy_settings_btn").performClick()

        assertEquals(
            listOf(SheetTarget.DomainLists, SheetTarget.Saved, SheetTarget.Settings),
            opened,
        )
    }

    private fun render(
        isRunning: Boolean,
        strategies: List<StrategyResult> = emptyList(),
        savedStrategies: List<SavedStrategy> = emptyList(),
        evolutionState: EvolutionUiState? = null,
        runSummary: String = "",
        settings: StrategyTestSettings = StrategyTestSettings(),
        onBack: () -> Unit = {},
        onShowSheet: (SheetTarget) -> Unit = {},
        onModeChange: (Boolean) -> Unit = {},
        onRunToggle: () -> Unit = {},
        onApply: (String) -> Unit = {},
        onToggleSave: (String) -> Unit = {},
    ) {
        composeRule.setContent {
            OzeroTheme {
                StrategyTestScaffold(
                    isRunning = isRunning,
                    strategies = strategies,
                    savedStrategies = savedStrategies,
                    evolutionState = evolutionState,
                    runSummary = runSummary,
                    settings = settings,
                    onBack = onBack,
                    onShowSheet = onShowSheet,
                    onModeChange = onModeChange,
                    onRunToggle = onRunToggle,
                    onApply = onApply,
                    onToggleSave = onToggleSave,
                )
            }
        }
    }
}
