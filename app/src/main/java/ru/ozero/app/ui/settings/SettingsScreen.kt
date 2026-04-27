package ru.ozero.app.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import ru.ozero.app.settings.SettingsModel
import ru.ozero.commonvpn.split.SplitTunnelMode
import ru.ozero.coreapi.EngineId

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenAllowedApps: () -> Unit,
    onOpenServers: () -> Unit,
    onOpenAbout: () -> Unit = {},
    onOpenLogs: () -> Unit = {},
    onOpenBootLog: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val torState by viewModel.torInstall.collectAsStateWithLifecycle()
    val updateState by viewModel.update.collectAsStateWithLifecycle()
    BackHandler(onBack = onBack)
    SettingsScreenContent(
        state = state,
        torState = torState,
        updateState = updateState,
        onBack = onBack,
        nav = SettingsNavActions(
            onOpenAllowedApps = onOpenAllowedApps,
            onOpenServers = onOpenServers,
            onOpenAbout = onOpenAbout,
            onOpenLogs = onOpenLogs,
            onOpenBootLog = onOpenBootLog,
        ),
        onSplitModeChange = viewModel::onSplitModeChange,
        onIpv6Toggle = viewModel::onIpv6Toggle,
        onAutoStartToggle = viewModel::onAutoStartToggle,
        onManualEngineSelect = viewModel::onManualEngineSelect,
        torActions = TorActions(
            onInstall = viewModel::onInstallTor,
            onCancel = viewModel::onCancelTor,
        ),
        updateActions = UpdateActions(
            onCheck = viewModel::onCheckUpdate,
            onReset = viewModel::onResetUpdate,
        ),
    )
}

@Suppress("LongParameterList")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreenContent(
    state: SettingsUiState,
    torState: TorInstallUiState = TorInstallUiState.NotInstalled,
    updateState: UpdateUiState = UpdateUiState.Idle,
    onBack: () -> Unit,
    nav: SettingsNavActions = SettingsNavActions(onOpenAllowedApps = {}, onOpenServers = {}),
    onSplitModeChange: (SplitTunnelMode) -> Unit,
    onIpv6Toggle: (Boolean) -> Unit,
    onAutoStartToggle: (Boolean) -> Unit,
    onManualEngineSelect: (EngineId?) -> Unit,
    torActions: TorActions = TorActions(onInstall = {}, onCancel = {}),
    updateActions: UpdateActions = UpdateActions(onCheck = {}, onReset = {}),
) {
    Scaffold(
        modifier = Modifier.testTag(SettingsTestTags.SCREEN),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag(SettingsTestTags.BACK),
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
        when (state) {
            SettingsUiState.Loading -> LoadingBody(padding)
            is SettingsUiState.Content ->
                ContentBody(
                    padding = padding,
                    model = state.model,
                    torState = torState,
                    updateState = updateState,
                    nav = nav,
                    onSplitModeChange = onSplitModeChange,
                    onIpv6Toggle = onIpv6Toggle,
                    onAutoStartToggle = onAutoStartToggle,
                    onManualEngineSelect = onManualEngineSelect,
                    torActions = torActions,
                    updateActions = updateActions,
                )
        }
    }
}

@Composable
private fun LoadingBody(padding: PaddingValues) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(modifier = Modifier.testTag(SettingsTestTags.LOADING))
    }
}

@Suppress("LongParameterList")
@Composable
private fun ContentBody(
    padding: PaddingValues,
    model: SettingsModel,
    torState: TorInstallUiState,
    updateState: UpdateUiState,
    nav: SettingsNavActions,
    onSplitModeChange: (SplitTunnelMode) -> Unit,
    onIpv6Toggle: (Boolean) -> Unit,
    onAutoStartToggle: (Boolean) -> Unit,
    onManualEngineSelect: (EngineId?) -> Unit,
    torActions: TorActions,
    updateActions: UpdateActions,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(vertical = 8.dp),
    ) {
        item {
            ConnectionSection(
                splitMode = model.splitMode,
                manualEngine = model.manualEngine,
                onSplitModeChange = onSplitModeChange,
                onManualEngineSelect = onManualEngineSelect,
                onOpenServers = nav.onOpenServers,
            )
        }
        item { SectionDivider() }
        item {
            NetworkSection(
                ipv6Enabled = model.ipv6Enabled,
                onIpv6Toggle = onIpv6Toggle,
                onOpenAllowedApps = nav.onOpenAllowedApps,
            )
        }
        item { SectionDivider() }
        item {
            SecuritySection(
                autoStart = model.autoStart,
                onAutoStartToggle = onAutoStartToggle,
            )
        }
        item { SectionDivider() }
        item {
            UpdatesSection(
                state = updateState,
                onCheck = updateActions.onCheck,
            )
        }
        item { SectionDivider() }
        item {
            TorSection(
                state = torState,
                onInstall = torActions.onInstall,
                onCancel = torActions.onCancel,
            )
        }
        item { SectionDivider() }
        item { LogsSection(onOpenLogs = nav.onOpenLogs) }
        item { SectionDivider() }
        item { BootLogSection(onOpenBootLog = nav.onOpenBootLog) }
        item { SectionDivider() }
        item { AboutSection(onOpenAbout = nav.onOpenAbout) }
    }
}

@Composable
private fun LogsSection(onOpenLogs: () -> Unit) {
    androidx.compose.material3.OutlinedButton(
        onClick = onOpenLogs,
        modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
    ) {
        Text("Логи")
    }
}

@Composable
private fun BootLogSection(onOpenBootLog: () -> Unit) {
    androidx.compose.material3.OutlinedButton(
        onClick = onOpenBootLog,
        modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
    ) {
        Text("Boot log (persistent)")
    }
}

@Composable
private fun ConnectionSection(
    splitMode: SplitTunnelMode,
    manualEngine: EngineId?,
    onSplitModeChange: (SplitTunnelMode) -> Unit,
    onManualEngineSelect: (EngineId?) -> Unit,
    onOpenServers: () -> Unit,
) {
    SectionHeader(R.string.settings_section_connection, SettingsTestTags.SECTION_CONNECTION)
    SubsectionLabel(stringResource(R.string.settings_split_mode_label))
    SplitTunnelMode.entries.forEach { mode ->
        RadioRow(
            selected = mode == splitMode,
            label = stringResource(mode.labelRes()),
            tag = SettingsTestTags.SPLIT_MODE_PREFIX + mode.name,
            onClick = { onSplitModeChange(mode) },
        )
    }
    SubsectionLabel(stringResource(R.string.settings_manual_engine_title))
    Text(
        text = stringResource(R.string.settings_manual_engine_summary),
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
    )
    RadioRow(
        selected = manualEngine == null,
        label = stringResource(R.string.settings_manual_engine_auto),
        tag = SettingsTestTags.MANUAL_ENGINE_AUTO,
        onClick = { onManualEngineSelect(null) },
    )
    EngineId.entries.forEach { engine ->
        RadioRow(
            selected = manualEngine == engine,
            label = engine.name,
            tag = SettingsTestTags.MANUAL_ENGINE_PREFIX + engine.name,
            onClick = { onManualEngineSelect(engine) },
        )
    }
    NavRow(
        title = stringResource(R.string.settings_servers_title),
        summary = stringResource(R.string.settings_servers_summary),
        tag = SettingsTestTags.SERVERS_ROW,
        onClick = onOpenServers,
        enabled = true,
    )
}

@Composable
private fun NetworkSection(
    ipv6Enabled: Boolean,
    onIpv6Toggle: (Boolean) -> Unit,
    onOpenAllowedApps: () -> Unit,
) {
    SectionHeader(R.string.settings_section_network, SettingsTestTags.SECTION_NETWORK)
    SwitchRow(
        title = stringResource(R.string.settings_ipv6_title),
        summary = stringResource(R.string.settings_ipv6_summary),
        checked = ipv6Enabled,
        tag = SettingsTestTags.IPV6_SWITCH,
        onCheckedChange = onIpv6Toggle,
    )
    NavRow(
        title = stringResource(R.string.settings_allowed_apps_title),
        summary = stringResource(R.string.settings_allowed_apps_open_summary),
        tag = SettingsTestTags.ALLOWED_APPS_ROW,
        onClick = onOpenAllowedApps,
        enabled = true,
    )
}

@Composable
private fun SecuritySection(
    autoStart: Boolean,
    onAutoStartToggle: (Boolean) -> Unit,
) {
    SectionHeader(R.string.settings_section_security, SettingsTestTags.SECTION_SECURITY)
    SwitchRow(
        title = stringResource(R.string.settings_auto_start_title),
        summary = stringResource(R.string.settings_auto_start_summary),
        checked = autoStart,
        tag = SettingsTestTags.AUTO_START_SWITCH,
        onCheckedChange = onAutoStartToggle,
    )
}

@Composable
private fun UpdatesSection(
    state: UpdateUiState,
    onCheck: () -> Unit,
) {
    SectionHeader(R.string.settings_section_updates, SettingsTestTags.SECTION_UPDATES)
    val summary = when (state) {
        UpdateUiState.Idle -> stringResource(R.string.settings_check_update_summary)
        UpdateUiState.Checking -> stringResource(R.string.settings_update_state_checking)
        is UpdateUiState.Downloading ->
            stringResource(R.string.settings_update_state_downloading, state.percent)
        UpdateUiState.Verifying -> stringResource(R.string.settings_update_state_verifying)
        UpdateUiState.Installing -> stringResource(R.string.settings_update_state_installing)
        UpdateUiState.UpToDate -> stringResource(R.string.settings_update_state_uptodate)
        is UpdateUiState.Failed ->
            stringResource(R.string.settings_update_state_failed, state.reason)
    }
    val enabled = when (state) {
        UpdateUiState.Idle, UpdateUiState.UpToDate -> true
        is UpdateUiState.Failed -> true
        UpdateUiState.Checking, UpdateUiState.Verifying, UpdateUiState.Installing -> false
        is UpdateUiState.Downloading -> false
    }
    NavRow(
        title = stringResource(R.string.settings_check_update_title),
        summary = summary,
        tag = SettingsTestTags.CHECK_UPDATE_ROW,
        onClick = onCheck,
        enabled = enabled,
    )
}

@Composable
private fun TorSection(
    state: TorInstallUiState,
    onInstall: () -> Unit,
    onCancel: () -> Unit,
) {
    SectionHeader(R.string.settings_section_tor, SettingsTestTags.SECTION_TOR)
    Text(
        text = stringResource(R.string.settings_tor_summary),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )
    when (state) {
        TorInstallUiState.NotInstalled -> {
            Button(
                onClick = onInstall,
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .testTag(SettingsTestTags.TOR_INSTALL_BUTTON),
            ) {
                Text(stringResource(R.string.settings_tor_install))
            }
        }
        is TorInstallUiState.Installing -> {
            LinearProgressIndicator(
                progress = { state.percent / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .testTag(SettingsTestTags.TOR_PROGRESS),
            )
            Text(
                text = stringResource(R.string.settings_tor_progress, state.percent),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .testTag(SettingsTestTags.TOR_CANCEL_BUTTON),
            ) {
                Text(stringResource(R.string.settings_tor_cancel))
            }
        }
        TorInstallUiState.Installed -> {
            Text(
                text = stringResource(R.string.settings_tor_installed),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .testTag(SettingsTestTags.TOR_INSTALLED_LABEL),
            )
        }
        is TorInstallUiState.Failed -> {
            Text(
                text = stringResource(R.string.settings_tor_failed, state.reason),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .testTag(SettingsTestTags.TOR_FAILED_LABEL),
            )
            Button(
                onClick = onInstall,
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .testTag(SettingsTestTags.TOR_RETRY_BUTTON),
            ) {
                Text(stringResource(R.string.settings_tor_retry))
            }
        }
    }
}

@Composable
private fun AboutSection(onOpenAbout: () -> Unit) {
    SectionHeader(R.string.settings_section_about, SettingsTestTags.SECTION_ABOUT)
    Text(
        text = stringResource(R.string.settings_about_summary),
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
    NavRow(
        title = stringResource(R.string.about_title),
        summary = "",
        tag = "settings_about_row",
        onClick = onOpenAbout,
        enabled = true,
    )
}

@Composable
private fun SectionHeader(
    @androidx.annotation.StringRes titleRes: Int,
    tag: String,
) {
    Text(
        text = stringResource(titleRes),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag(tag),
    )
}

@Composable
private fun SubsectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

@Composable
private fun RadioRow(
    selected: Boolean,
    label: String,
    tag: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(text = label, modifier = Modifier.padding(start = 12.dp))
    }
}

@Composable
private fun SwitchRow(
    title: String,
    summary: String,
    checked: Boolean,
    tag: String,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.fillMaxWidth(0.78f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun NavRow(
    title: String,
    summary: String,
    tag: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val rowModifier = Modifier
        .fillMaxWidth()
        .let { if (enabled) it.clickable(onClick = onClick) else it }
        .padding(horizontal = 16.dp, vertical = 12.dp)
        .testTag(tag)
    val alpha = if (enabled) 1f else 0.5f
    Column(modifier = rowModifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
        )
        Text(
            text = summary,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha * 0.6f),
        )
    }
}

@Composable
private fun SectionDivider() {
    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
}

@androidx.annotation.StringRes
private fun SplitTunnelMode.labelRes(): Int =
    when (this) {
        SplitTunnelMode.ALL -> R.string.settings_split_mode_all
        SplitTunnelMode.BYPASS_LAN -> R.string.settings_split_mode_bypass_lan
        SplitTunnelMode.ALLOWLIST -> R.string.settings_split_mode_allowlist
        SplitTunnelMode.BLOCKLIST -> R.string.settings_split_mode_blocklist
    }
