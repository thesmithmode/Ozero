package ru.ozero.app.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.ozero.app.R
import ru.ozero.enginescore.settings.SettingsModel

@Composable
fun LanguageScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val currentTag = (state as? SettingsUiState.Content)?.model?.uiLocaleTag
    BackHandler(onBack = onBack)
    LanguageScreenContent(
        currentTag = currentTag,
        onBack = onBack,
        onSelect = viewModel::onUiLocaleSelect,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageScreenContent(
    currentTag: String?,
    onBack: () -> Unit,
    onSelect: (String?) -> Unit,
) {
    Scaffold(
        modifier = Modifier.testTag(LanguageScreenTestTags.SCREEN),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_language_title)) },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag(LanguageScreenTestTags.BACK),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.settings_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        LanguageList(
            padding = padding,
            currentTag = currentTag,
            onSelect = onSelect,
        )
    }
}

@Composable
private fun LanguageList(
    padding: PaddingValues,
    currentTag: String?,
    onSelect: (String?) -> Unit,
) {
    val allLocales = listOf(
        null to R.string.settings_language_system,
        SettingsModel.LOCALE_RU to R.string.settings_language_ru,
        SettingsModel.LOCALE_EN to R.string.settings_language_en,
        SettingsModel.LOCALE_ZH_CN to R.string.settings_language_zh_cn,
        SettingsModel.LOCALE_ES to R.string.settings_language_es,
        SettingsModel.LOCALE_AR to R.string.settings_language_ar,
        SettingsModel.LOCALE_FR to R.string.settings_language_fr,
        SettingsModel.LOCALE_HI to R.string.settings_language_hi,
        SettingsModel.LOCALE_PT to R.string.settings_language_pt,
        SettingsModel.LOCALE_ID to R.string.settings_language_id,
        SettingsModel.LOCALE_DE to R.string.settings_language_de,
        SettingsModel.LOCALE_JA to R.string.settings_language_ja,
    )
    val supported = LocaleApplier.SUPPORTED_TAGS.toSet()
    val locales = allLocales.filter { (tag, _) -> (tag ?: "") in supported }
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
    ) {
        items(locales) { (tag, labelRes) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = currentTag == tag,
                        onClick = { onSelect(tag) },
                        role = Role.RadioButton,
                    )
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .testTag(LanguageScreenTestTags.rowTag(tag)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = currentTag == tag, onClick = null)
                Text(
                    text = stringResource(labelRes),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
        }
    }
}

object LanguageScreenTestTags {
    const val SCREEN = "language_screen"
    const val BACK = "language_back"
    private const val ROW_PREFIX = "language_row_"
    fun rowTag(tag: String?): String = ROW_PREFIX + (tag ?: "system")
}
