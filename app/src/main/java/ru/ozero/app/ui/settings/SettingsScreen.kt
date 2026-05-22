@file:Suppress("TooManyFunctions", "LongParameterList")

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
import android.content.Intent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.ozero.app.R
import ru.ozero.enginescore.EngineId
import ru.ozero.enginescore.settings.AppMode
import ru.ozero.enginescore.settings.SettingsModel

@Suppress("LongParameterList")
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenAllowedApps: () -> Unit,
    onOpenServers: () -> Unit,
    onOpenAbout: () -> Unit = {},
    onOpenLogs: () -> Unit = {},
    onOpenByeDpiEngineSettings: () -> Unit = {},
    onOpenUrnetworkSettings: () -> Unit = {},
    onOpenWarpSettings: () -> Unit = {},
    onOpenManualServer: () -> Unit = {},
    onOpenStatsHistory: () -> Unit = {},
    onOpenDiagnostics: () -> Unit = {},
    onOpenBackup: () -> Unit = {},
    onOpenAutoModeSettings: () -> Unit = {},
    onOpenLanguage: () -> Unit = {},
    onOpenTelegramProxySettings: () -> Unit = {},
    onOpenMasterDnsSettings: () -> Unit = {},
    onOpenFptnSettings: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    BackHandler(onBack = onBack)
    SettingsScreenContent(
        state = state,
        onBack = onBack,
        nav = SettingsNavActions(
            onOpenAllowedApps = onOpenAllowedApps,
            onOpenServers = onOpenServers,
            onOpenAbout = onOpenAbout,
            onOpenLogs = onOpenLogs,
            onOpenByeDpiEngineSettings = onOpenByeDpiEngineSettings,
            onOpenUrnetworkSettings = onOpenUrnetworkSettings,
            onOpenWarpSettings = onOpenWarpSettings,
            onOpenManualServer = onOpenManualServer,
            onOpenStatsHistory = onOpenStatsHistory,
            onOpenDiagnostics = onOpenDiagnostics,
            onOpenBackup = onOpenBackup,
            onOpenAutoModeSettings = onOpenAutoModeSettings,
            onOpenLanguage = onOpenLanguage,
            onOpenTelegramProxySettings = onOpenTelegramProxySettings,
            onOpenMasterDnsSettings = onOpenMasterDnsSettings,
            onOpenFptnSettings = onOpenFptnSettings,
        ),
        onIpv6Toggle = viewModel::onIpv6Toggle,
        onAutoStartToggle = viewModel::onAutoStartToggle,
        onKillswitchToggle = viewModel::onKillswitchToggle,
        onAlwaysOnBannerDismissed = viewModel::onAlwaysOnBannerDismissed,
        onManualEngineSelect = viewModel::onManualEngineSelect,
        onAppModeSelect = viewModel::onAppModeSelect,
    )
}

@Suppress("LongParameterList")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreenContent(
    state: SettingsUiState,
    onBack: () -> Unit,
    nav: SettingsNavActions = SettingsNavActions(onOpenAllowedApps = {}, onOpenServers = {}),
    onIpv6Toggle: (Boolean) -> Unit,
    onAutoStartToggle: (Boolean) -> Unit,
    onKillswitchToggle: (Boolean) -> Unit = {},
    onAlwaysOnBannerDismissed: () -> Unit = {},
    onManualEngineSelect: (EngineId?) -> Unit,
    onAppModeSelect: (AppMode) -> Unit = {},
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
                    nav = nav,
                    onIpv6Toggle = onIpv6Toggle,
                    onAutoStartToggle = onAutoStartToggle,
                    onKillswitchToggle = onKillswitchToggle,
                    onAlwaysOnBannerDismissed = onAlwaysOnBannerDismissed,
                    onManualEngineSelect = onManualEngineSelect,
                    onAppModeSelect = onAppModeSelect,
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
    nav: SettingsNavActions,
    onIpv6Toggle: (Boolean) -> Unit,
    onAutoStartToggle: (Boolean) -> Unit,
    onKillswitchToggle: (Boolean) -> Unit = {},
    onAlwaysOnBannerDismissed: () -> Unit = {},
    onManualEngineSelect: (EngineId?) -> Unit,
    onAppModeSelect: (AppMode) -> Unit = {},
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(vertical = 8.dp),
    ) {
        item {
            AppModeSection(
                currentMode = model.appMode,
                onSelect = onAppModeSelect,
            )
        }
        item { SectionDivider() }
        item {
            ConnectionSection(
                manualEngine = model.manualEngine,
                onManualEngineSelect = onManualEngineSelect,
                onOpenServers = nav.onOpenServers,
            )
        }
        if (model.appMode == AppMode.EXPERT) {
            item {
                AutoModeRow(onOpen = nav.onOpenAutoModeSettings)
            }
        }
        item { SectionDivider() }
        item {
            EnginesSection(
                onOpenByeDpi = nav.onOpenByeDpiEngineSettings,
                onOpenUrnetwork = nav.onOpenUrnetworkSettings,
                onOpenWarp = nav.onOpenWarpSettings,
                onOpenManualServer = nav.onOpenManualServer,
                onOpenTelegramProxy = nav.onOpenTelegramProxySettings,
                onOpenMasterDns = nav.onOpenMasterDnsSettings,
                onOpenFptn = nav.onOpenFptnSettings,
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
                killswitch = model.killswitchEnabled,
                alwaysOnBannerDismissed = model.alwaysOnBannerDismissed,
                onAutoStartToggle = onAutoStartToggle,
                onKillswitchToggle = onKillswitchToggle,
                onAlwaysOnBannerDismiss = onAlwaysOnBannerDismissed,
            )
        }
        item { SectionDivider() }
        item { StatsHistorySection(onOpenStats = nav.onOpenStatsHistory) }
        item { SectionDivider() }
        item { DiagnosticsSection(onOpenDiagnostics = nav.onOpenDiagnostics) }
        item { SectionDivider() }
        item { BackupSection(onOpenBackup = nav.onOpenBackup) }
        item { SectionDivider() }
        item {
            LanguageNavRow(
                currentTag = model.uiLocaleTag,
                onOpen = nav.onOpenLanguage,
            )
        }
        item { SectionDivider() }
        item { LogsSection(onOpenLogs = nav.onOpenLogs) }
        item { SectionDivider() }
        item { AboutSection(onOpenAbout = nav.onOpenAbout) }
    }
}

@Composable
private fun AutoModeRow(onOpen: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .testTag(SettingsTestTags.AUTO_MODE_OPEN_ROW)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            text = stringResource(R.string.settings_auto_mode_open_title),
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            text = stringResource(R.string.settings_auto_mode_open_summary),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )
    }
}

@Composable
private fun AppModeSection(
    currentMode: AppMode,
    onSelect: (AppMode) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(SettingsTestTags.APP_MODE_SECTION),
    ) {
        Text(
            text = stringResource(R.string.settings_app_mode_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .selectable(
                    selected = currentMode == AppMode.SIMPLE,
                    onClick = { onSelect(AppMode.SIMPLE) },
                    role = Role.RadioButton,
                )
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .testTag(SettingsTestTags.APP_MODE_SIMPLE),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = currentMode == AppMode.SIMPLE, onClick = null)
            Column(modifier = Modifier.padding(start = 12.dp)) {
                Text(
                    text = stringResource(R.string.settings_app_mode_simple),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = stringResource(R.string.settings_app_mode_simple_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .selectable(
                    selected = currentMode == AppMode.EXPERT,
                    onClick = { onSelect(AppMode.EXPERT) },
                    role = Role.RadioButton,
                )
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .testTag(SettingsTestTags.APP_MODE_EXPERT),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = currentMode == AppMode.EXPERT, onClick = null)
            Column(modifier = Modifier.padding(start = 12.dp)) {
                Text(
                    text = stringResource(R.string.settings_app_mode_expert),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = stringResource(R.string.settings_app_mode_expert_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
        }
    }
}

@Composable
private fun LanguageNavRow(
    currentTag: String?,
    onOpen: () -> Unit,
) {
    val labelRes = when (currentTag) {
        SettingsModel.LOCALE_RU -> R.string.settings_language_ru
        SettingsModel.LOCALE_EN -> R.string.settings_language_en
        SettingsModel.LOCALE_ZH_CN -> R.string.settings_language_zh_cn
        SettingsModel.LOCALE_ES -> R.string.settings_language_es
        SettingsModel.LOCALE_AR -> R.string.settings_language_ar
        SettingsModel.LOCALE_FR -> R.string.settings_language_fr
        SettingsModel.LOCALE_HI -> R.string.settings_language_hi
        SettingsModel.LOCALE_PT -> R.string.settings_language_pt
        SettingsModel.LOCALE_ID -> R.string.settings_language_id
        SettingsModel.LOCALE_DE -> R.string.settings_language_de
        SettingsModel.LOCALE_JA -> R.string.settings_language_ja
        else -> R.string.settings_language_system
    }
    NavRow(
        title = stringResource(R.string.settings_language_title),
        summary = stringResource(labelRes),
        tag = SettingsTestTags.LANGUAGE_ROW,
        enabled = true,
        onClick = onOpen,
    )
}

@Composable
private fun StatsHistorySection(onOpenStats: () -> Unit) {
    NavRow(
        title = stringResource(R.string.stats_history_title),
        summary = stringResource(R.string.stats_history_summary),
        tag = "settings_stats_history_row",
        onClick = onOpenStats,
        enabled = true,
    )
}

@Composable
private fun BackupSection(onOpenBackup: () -> Unit) {
    NavRow(
        title = stringResource(R.string.backup_title),
        summary = stringResource(R.string.backup_summary),
        tag = "settings_backup_row",
        onClick = onOpenBackup,
        enabled = true,
    )
}

@Composable
private fun DiagnosticsSection(onOpenDiagnostics: () -> Unit) {
    NavRow(
        title = stringResource(R.string.diag_title),
        summary = stringResource(R.string.diag_idle_hint),
        tag = "settings_diagnostics_row",
        onClick = onOpenDiagnostics,
        enabled = true,
    )
}

@Composable
private fun EnginesSection(
    onOpenByeDpi: () -> Unit,
    onOpenUrnetwork: () -> Unit,
    onOpenWarp: () -> Unit,
    onOpenManualServer: () -> Unit,
    onOpenTelegramProxy: () -> Unit,
    onOpenMasterDns: () -> Unit,
    onOpenFptn: () -> Unit,
) {
    NavRow(
        title = stringResource(R.string.settings_byedpi_title),
        summary = stringResource(R.string.settings_byedpi_summary),
        tag = "settings_byedpi_engine_row",
        onClick = onOpenByeDpi,
        enabled = true,
    )
    NavRow(
        title = stringResource(R.string.settings_urnetwork_title),
        summary = stringResource(R.string.settings_urnetwork_summary),
        tag = "settings_urnetwork_row",
        onClick = onOpenUrnetwork,
        enabled = true,
    )
    val warpTitle = if (EngineId.WARP.isStub) {
        stringResource(R.string.settings_warp_title) +
            " (${stringResource(R.string.engine_wip_badge)})"
    } else {
        stringResource(R.string.settings_warp_title)
    }
    NavRow(
        title = warpTitle,
        summary = stringResource(R.string.settings_warp_summary),
        tag = "settings_warp_row",
        onClick = onOpenWarp,
        enabled = true,
    )
    NavRow(
        title = stringResource(R.string.settings_manual_server_title),
        summary = stringResource(R.string.settings_manual_server_summary),
        tag = "settings_manual_server_row",
        onClick = onOpenManualServer,
        enabled = true,
    )
    NavRow(
        title = stringResource(R.string.settings_telegram_proxy_title),
        summary = stringResource(R.string.settings_telegram_proxy_summary),
        tag = "settings_telegram_proxy_row",
        onClick = onOpenTelegramProxy,
        enabled = true,
    )
    NavRow(
        title = stringResource(R.string.settings_masterdns_title),
        summary = stringResource(R.string.settings_masterdns_summary),
        tag = "settings_masterdns_row",
        onClick = onOpenMasterDns,
        enabled = true,
    )
    NavRow(
        title = stringResource(R.string.settings_fptn_title),
        summary = stringResource(R.string.settings_fptn_summary),
        tag = "settings_fptn_row",
        onClick = onOpenFptn,
        enabled = true,
    )
}

@Composable
private fun LogsSection(onOpenLogs: () -> Unit) {
    androidx.compose.material3.OutlinedButton(
        onClick = onOpenLogs,
        modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
    ) {
        Text(stringResource(R.string.settings_logs))
    }
}

@Composable
private fun ConnectionSection(
    manualEngine: EngineId?,
    onManualEngineSelect: (EngineId?) -> Unit,
    onOpenServers: () -> Unit,
) {
    SectionHeader(R.string.settings_section_connection, SettingsTestTags.SECTION_CONNECTION)
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
        val label = if (engine.isStub) {
            "${engine.displayName} (${stringResource(R.string.engine_wip_badge)})"
        } else {
            engine.displayName
        }
        RadioRow(
            selected = manualEngine == engine,
            label = label,
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
    killswitch: Boolean,
    alwaysOnBannerDismissed: Boolean,
    onAutoStartToggle: (Boolean) -> Unit,
    onKillswitchToggle: (Boolean) -> Unit,
    onAlwaysOnBannerDismiss: () -> Unit,
) {
    SectionHeader(R.string.settings_section_security, SettingsTestTags.SECTION_SECURITY)
    SwitchRow(
        title = stringResource(R.string.settings_auto_start_title),
        summary = stringResource(R.string.settings_auto_start_summary),
        checked = autoStart,
        tag = SettingsTestTags.AUTO_START_SWITCH,
        onCheckedChange = onAutoStartToggle,
    )
    SwitchRow(
        title = stringResource(R.string.settings_killswitch_title),
        summary = stringResource(R.string.settings_killswitch_summary),
        checked = killswitch,
        tag = SettingsTestTags.KILLSWITCH_SWITCH,
        onCheckedChange = onKillswitchToggle,
    )
    if (killswitch && !alwaysOnBannerDismissed) {
        AlwaysOnVpnBanner(onDismiss = onAlwaysOnBannerDismiss)
    }
}

@Composable
private fun AlwaysOnVpnBanner(onDismiss: () -> Unit) {
    val context = LocalContext.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, end = 4.dp, bottom = 0.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.settings_always_on_banner_title),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = stringResource(R.string.settings_always_on_banner_body),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = null,
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = 8.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(
                onClick = {
                    runCatching {
                        context.startActivity(
                            Intent("android.net.vpn.SETTINGS")
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                },
            ) {
                Text(stringResource(R.string.settings_always_on_banner_configure))
            }
        }
    }
}

@Composable
private fun AboutSection(onOpenAbout: () -> Unit) {
    SectionHeader(R.string.settings_section_about, SettingsTestTags.SECTION_ABOUT)
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
