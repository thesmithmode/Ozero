package ru.ozero.app.ui.settings.engines

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.ozero.app.ui.theme.OzeroTheme
import ru.ozero.enginescore.settings.ByeDpiUiSettings
import ru.ozero.enginescore.settings.ByeDpiUiSettings.DesyncMethod

@RunWith(AndroidJUnit4::class)
class ByeDpiUiModeSectionTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun integerFieldParsesDigitsAndReportsUpdatedSettings() {
        val updates = mutableListOf<ByeDpiUiSettings>()

        render(onChange = { updates += it })

        composeRule.onNodeWithTag("byedpi_ui_max_connections")
            .performTextClearance()
        composeRule.onNodeWithTag("byedpi_ui_max_connections")
            .performTextInput("512abc")

        assertEquals(512, updates.last().maxConnections)
    }

    @Test
    fun toggleReportsUpdatedBooleanSetting() {
        val updates = mutableListOf<ByeDpiUiSettings>()

        render(onChange = { updates += it })

        composeRule.onNodeWithTag("byedpi_ui_no_domain").performClick()

        assertEquals(!ByeDpiUiSettings.DEFAULT.noDomain, updates.last().noDomain)
    }

    @Test
    fun methodChipReportsSelectedDesyncMethod() {
        val updates = mutableListOf<ByeDpiUiSettings>()

        render(onChange = { updates += it })

        composeRule.onNodeWithTag("byedpi_ui_method_${DesyncMethod.FAKE.name}").performClick()

        assertEquals(DesyncMethod.FAKE, updates.last().desyncMethod)
    }

    @Test
    fun oobCharFieldKeepsOnlySingleCharacter() {
        val updates = mutableListOf<ByeDpiUiSettings>()

        render(
            settings = ByeDpiUiSettings.DEFAULT.copy(desyncMethod = DesyncMethod.OOB, oobChar = ""),
            onChange = { updates += it },
        )

        composeRule.onNodeWithTag("byedpi_ui_oob_char").performTextInput("xy")

        assertEquals("x", updates.last().oobChar)
    }

    private fun render(
        settings: ByeDpiUiSettings = ByeDpiUiSettings.DEFAULT,
        onChange: (ByeDpiUiSettings) -> Unit,
    ) {
        composeRule.setContent {
            var current by remember { mutableStateOf(settings) }
            OzeroTheme {
                ByeDpiUiModeSection(
                    settings = current,
                    onChange = {
                        current = it
                        onChange(it)
                    },
                )
            }
        }
    }
}
