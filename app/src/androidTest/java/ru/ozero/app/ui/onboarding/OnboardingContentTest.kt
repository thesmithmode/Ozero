package ru.ozero.app.ui.onboarding

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.ozero.app.ui.theme.OzeroTheme
import ru.ozero.enginescore.settings.AppMode

@RunWith(AndroidJUnit4::class)
class OnboardingContentTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun languageStepSelectsLocaleAndCanSkip() {
        val events = mutableListOf<String>()

        render(
            pageIndex = 0,
            currentLocaleTag = null,
            onLocaleSelect = { events += "locale:${it ?: "system"}" },
            onSkip = { events += "skip" },
        )

        composeRule.onNodeWithTag("onboarding_locale_en")
            .assertIsDisplayed()
            .performClick()
        composeRule.onNodeWithTag("onboarding_skip").performClick()

        assertEquals(listOf("locale:en", "skip"), events)
    }

    @Test
    fun modeStepSelectsExpertAndFinishes() {
        val events = mutableListOf<String>()

        render(
            pageIndex = 4,
            currentAppMode = AppMode.SIMPLE,
            onAppModeSelect = { events += "mode:$it" },
            onFinish = { events += "finish" },
        )

        composeRule.onNodeWithTag("onboarding_mode_expert")
            .assertIsDisplayed()
            .performClick()
        composeRule.onNodeWithTag("onboarding_finish").performClick()

        assertEquals(listOf("mode:EXPERT", "finish"), events)
    }

    @Test
    fun navigationRemainsReachableAtLargeFontScale() {
        render(pageIndex = 4, fontScale = 2f)

        composeRule.onNodeWithTag("onboarding_skip").assertIsDisplayed()
        composeRule.onNodeWithTag("onboarding_finish").assertIsDisplayed()
    }

    private fun render(
        pageIndex: Int,
        currentLocaleTag: String? = null,
        currentAppMode: AppMode = AppMode.SIMPLE,
        onLocaleSelect: (String?) -> Unit = {},
        onAppModeSelect: (AppMode) -> Unit = {},
        onNext: () -> Unit = {},
        onSkip: () -> Unit = {},
        onFinish: () -> Unit = {},
        fontScale: Float = 1f,
    ) {
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, fontScale)) {
                OzeroTheme {
                    OnboardingContent(
                        pageIndex = pageIndex,
                        currentLocaleTag = currentLocaleTag,
                        currentAppMode = currentAppMode,
                        onLocaleSelect = onLocaleSelect,
                        onAppModeSelect = onAppModeSelect,
                        onNext = onNext,
                        onSkip = onSkip,
                        onFinish = onFinish,
                    )
                }
            }
        }
    }
}
