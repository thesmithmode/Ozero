package ru.ozero.app.ui.backup

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
import ru.ozero.corebackup.BackupCategory

@RunWith(AndroidJUnit4::class)
class BackupCategoryPickerDialogTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun confirmReturnsOnlyAvailableSelectedCategories() {
        val confirmed = mutableListOf<Set<BackupCategory>>()

        render(
            available = setOf(BackupCategory.GENERAL_SETTINGS, BackupCategory.BYEDPI),
            initiallySelected = setOf(
                BackupCategory.GENERAL_SETTINGS,
                BackupCategory.BYEDPI,
                BackupCategory.WARP,
            ),
            onConfirm = { confirmed += it },
        )

        composeRule.onNodeWithTag(BackupTestTags.CATEGORY_CONFIRM)
            .assertIsEnabled()
            .performClick()

        assertEquals(
            listOf(setOf(BackupCategory.GENERAL_SETTINGS, BackupCategory.BYEDPI)),
            confirmed,
        )
    }

    @Test
    fun confirmDisabledWhenSelectionBecomesEmpty() {
        render(
            available = setOf(BackupCategory.GENERAL_SETTINGS),
            initiallySelected = setOf(BackupCategory.GENERAL_SETTINGS),
        )

        composeRule.onNodeWithTag(BackupTestTags.CATEGORY_CHECKBOX_PREFIX + BackupCategory.GENERAL_SETTINGS.name)
            .performClick()

        composeRule.onNodeWithTag(BackupTestTags.CATEGORY_CONFIRM).assertIsNotEnabled()
    }

    @Test
    fun unavailableCategoryCannotBeSelected() {
        render(
            available = setOf(BackupCategory.GENERAL_SETTINGS),
            initiallySelected = emptySet(),
        )

        composeRule.onNodeWithTag(BackupTestTags.CATEGORY_CHECKBOX_PREFIX + BackupCategory.WARP.name)
            .assertIsNotEnabled()
    }

    @Test
    fun cancelInvokesDismissCallback() {
        var dismissed = false

        render(
            available = BackupCategory.ALL,
            initiallySelected = BackupCategory.ALL,
            onDismiss = { dismissed = true },
        )

        composeRule.onNodeWithTag(BackupTestTags.CATEGORY_CANCEL).performClick()

        assertEquals(true, dismissed)
    }

    private fun render(
        available: Set<BackupCategory>,
        initiallySelected: Set<BackupCategory>,
        onConfirm: (Set<BackupCategory>) -> Unit = {},
        onDismiss: () -> Unit = {},
    ) {
        composeRule.setContent {
            OzeroTheme {
                CategoryPickerDialog(
                    title = "Backup",
                    available = available,
                    initiallySelected = initiallySelected,
                    onConfirm = onConfirm,
                    onDismiss = onDismiss,
                )
            }
        }
    }
}
