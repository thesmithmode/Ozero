package ru.ozero.app.ui.settings

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
import ru.ozero.enginescore.settings.SettingsModel

@RunWith(AndroidJUnit4::class)
class LanguageScreenContentTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun languageRowsSelectLocaleAndSystemLocale() {
        val selected = mutableListOf<String?>()

        render(currentTag = SettingsModel.LOCALE_RU, onSelect = { selected += it })

        composeRule.onNodeWithTag(LanguageScreenTestTags.rowTag(SettingsModel.LOCALE_EN))
            .assertIsDisplayed()
            .performClick()
        composeRule.onNodeWithTag(LanguageScreenTestTags.rowTag(null))
            .assertIsDisplayed()
            .performClick()

        assertEquals(listOf(SettingsModel.LOCALE_EN, null), selected)
    }

    @Test
    fun backButtonInvokesCallback() {
        var backCount = 0

        render(currentTag = null, onBack = { backCount++ })

        composeRule.onNodeWithTag(LanguageScreenTestTags.BACK).performClick()

        assertEquals(1, backCount)
    }

    private fun render(
        currentTag: String?,
        onBack: () -> Unit = {},
        onSelect: (String?) -> Unit = {},
    ) {
        composeRule.setContent {
            OzeroTheme {
                LanguageScreenContent(
                    currentTag = currentTag,
                    onBack = onBack,
                    onSelect = onSelect,
                )
            }
        }
    }
}
