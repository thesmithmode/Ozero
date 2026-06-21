package ru.ozero.app.ui.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import ru.ozero.app.ui.theme.OzeroTheme
import ru.ozero.enginescore.EngineId

class AutoPriorityContentTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun displaysRealEngines() {
        composeRule.setContent {
            OzeroTheme {
                AutoPriorityContent(
                    priority = listOf(
                        EngineId.FPTN,
                        EngineId.WARP,
                        EngineId.SINGBOX,
                        EngineId.BYEDPI,
                        EngineId.URNETWORK,
                    ),
                    onMove = { _, _ -> },
                )
            }
        }

        composeRule.onNodeWithTag(SettingsTestTags.AUTO_PRIORITY_SECTION).assertIsDisplayed()
        composeRule.onNodeWithTag(SettingsTestTags.AUTO_PRIORITY_ITEM_PREFIX + EngineId.WARP.name)
            .assertIsDisplayed()
        composeRule.onNodeWithTag(SettingsTestTags.AUTO_PRIORITY_ITEM_PREFIX + EngineId.BYEDPI.name)
            .assertIsDisplayed()
        composeRule.onNodeWithTag(SettingsTestTags.AUTO_PRIORITY_ITEM_PREFIX + EngineId.URNETWORK.name)
            .assertIsDisplayed()
        composeRule.onNodeWithTag(SettingsTestTags.AUTO_PRIORITY_ITEM_PREFIX + EngineId.FPTN.name)
            .assertIsDisplayed()
        composeRule.onNodeWithTag(SettingsTestTags.AUTO_PRIORITY_ITEM_PREFIX + EngineId.SINGBOX.name)
            .assertIsDisplayed()
    }

    @Test
    fun moveButtonsRespectBoundariesAndInvokeCallbacks() {
        val moves = mutableListOf<Pair<EngineId, Int>>()

        composeRule.setContent {
            OzeroTheme {
                AutoPriorityContent(
                    priority = listOf(EngineId.WARP, EngineId.BYEDPI, EngineId.URNETWORK),
                    onMove = { engine, delta -> moves.add(engine to delta) },
                )
            }
        }

        composeRule.onNodeWithTag(SettingsTestTags.AUTO_PRIORITY_UP_PREFIX + EngineId.WARP.name)
            .assertIsNotEnabled()
        composeRule.onNodeWithTag(SettingsTestTags.AUTO_PRIORITY_DOWN_PREFIX + EngineId.WARP.name)
            .assertIsEnabled()
            .performClick()
        composeRule.onNodeWithTag(SettingsTestTags.AUTO_PRIORITY_UP_PREFIX + EngineId.BYEDPI.name)
            .assertIsEnabled()
            .performClick()
        composeRule.onNodeWithTag(SettingsTestTags.AUTO_PRIORITY_DOWN_PREFIX + EngineId.BYEDPI.name)
            .assertIsEnabled()
            .performClick()
        composeRule.onNodeWithTag(SettingsTestTags.AUTO_PRIORITY_UP_PREFIX + EngineId.URNETWORK.name)
            .assertIsEnabled()
            .performClick()
        composeRule.onNodeWithTag(SettingsTestTags.AUTO_PRIORITY_DOWN_PREFIX + EngineId.URNETWORK.name)
            .assertIsNotEnabled()

        assertEquals(
            listOf(
                EngineId.WARP to 1,
                EngineId.BYEDPI to -1,
                EngineId.BYEDPI to 1,
                EngineId.URNETWORK to -1,
            ),
            moves,
        )
    }
}
