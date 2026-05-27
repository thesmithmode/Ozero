@file:Suppress("TooManyFunctions", "LongParameterList")

package ru.ozero.desktop.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import ru.ozero.desktop.model.AppMode
import ru.ozero.desktop.model.EngineId
import ru.ozero.desktop.strings.Strings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    currentAppMode: AppMode = AppMode.EXPERT,
    onOpenAllowedApps: () -> Unit = {},
    onOpenAbout: () -> Unit = {},
    onOpenLogs: () -> Unit = {},
    onAppModeSelect: (AppMode) -> Unit = {},
    onOpenEngineSettings: (EngineId) -> Unit = {},
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(Strings.SETTINGS_TITLE) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = Strings.SETTINGS_BACK)
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(vertical = 8.dp),
        ) {
            item { AppModeSection(currentMode = currentAppMode, onSelect = onAppModeSelect) }
            item { SectionDivider() }
            item {
                SectionHeader(Strings.SETTINGS_SECTION_CONNECTION)
                EnginesSection(onOpenEngineSettings)
            }
            item { SectionDivider() }
            item {
                SectionHeader(Strings.SETTINGS_SECTION_NETWORK)
                NavRow(
                    Strings.SETTINGS_ALLOWED_APPS_TITLE,
                    Strings.SETTINGS_ALLOWED_APPS_SUMMARY,
                    onClick = onOpenAllowedApps,
                )
            }
            item { SectionDivider() }
            item {
                NavRow(Strings.SETTINGS_LOGS, "", onClick = onOpenLogs)
            }
            item { SectionDivider() }
            item {
                SectionHeader(Strings.SETTINGS_SECTION_ABOUT)
                NavRow(Strings.ABOUT_TITLE, "", onClick = onOpenAbout)
            }
        }
    }
}

@Composable
private fun AppModeSection(currentMode: AppMode, onSelect: (AppMode) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = Strings.SETTINGS_APP_MODE_TITLE,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
        RadioRow(
            label = Strings.SETTINGS_APP_MODE_SIMPLE,
            hint = Strings.SETTINGS_APP_MODE_SIMPLE_HINT,
            isSelected = currentMode == AppMode.SIMPLE,
        ) { onSelect(AppMode.SIMPLE) }
        RadioRow(
            label = Strings.SETTINGS_APP_MODE_EXPERT,
            hint = Strings.SETTINGS_APP_MODE_EXPERT_HINT,
            isSelected = currentMode == AppMode.EXPERT,
        ) { onSelect(AppMode.EXPERT) }
    }
}

@Composable
private fun EnginesSection(onOpenEngineSettings: (EngineId) -> Unit) {
    NavRow(Strings.SETTINGS_BYEDPI_TITLE, Strings.SETTINGS_BYEDPI_SUMMARY,
        onClick = { onOpenEngineSettings(EngineId.BYEDPI) })
    NavRow(Strings.SETTINGS_URNETWORK_TITLE, Strings.SETTINGS_URNETWORK_SUMMARY,
        onClick = { onOpenEngineSettings(EngineId.URNETWORK) })
    NavRow(Strings.SETTINGS_WARP_TITLE, Strings.SETTINGS_WARP_SUMMARY,
        onClick = { onOpenEngineSettings(EngineId.WARP) })
    NavRow(Strings.SETTINGS_MASTERDNS_TITLE, Strings.SETTINGS_MASTERDNS_SUMMARY,
        onClick = { onOpenEngineSettings(EngineId.MASTERDNS) })
    NavRow(Strings.SETTINGS_FPTN_TITLE, Strings.SETTINGS_FPTN_SUMMARY,
        onClick = { onOpenEngineSettings(EngineId.FPTN) })
    NavRow(Strings.SETTINGS_SINGBOX_TITLE, Strings.SETTINGS_SINGBOX_SUMMARY,
        onClick = { onOpenEngineSettings(EngineId.SINGBOX) })
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
    )
}

@Composable
private fun RadioRow(label: String, hint: String, isSelected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = isSelected, onClick = onClick, role = Role.RadioButton)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = isSelected, onClick = null)
        Column(modifier = Modifier.padding(start = 12.dp)) {
            Text(text = label, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
        }
    }
}

@Composable
private fun NavRow(title: String, summary: String, onClick: () -> Unit = {}) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(text = title, style = MaterialTheme.typography.bodyLarge)
        if (summary.isNotEmpty()) {
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
        }
    }
}

@Composable
private fun SectionDivider() {
    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
}
