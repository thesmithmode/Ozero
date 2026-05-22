package ru.ozero.app.ui.settings.engines

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.ozero.app.R
import ru.ozero.enginefptn.FptnBypassMethod
import ru.ozero.enginefptn.FptnServer

private object FptnLinks {
    const val WEBSITE = "https://fptn.online"
    const val TELEGRAM_GROUP = "https://t.me/fptn_project"
    const val PLAY = "https://play.google.com/store/apps/details?id=com.filantrop.pvnclient"
    const val BOOSTY = "https://boosty.to/fptn"
    const val BOT = "https://t.me/fptn_bot"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FptnEngineSettingsScreen(
    onBack: () -> Unit,
    onOpenSplitTunnel: () -> Unit,
    viewModel: FptnEngineSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var tokenDraft by rememberSaveable { mutableStateOf("") }
    var experimentalVisible by remember { mutableStateOf(false) }

    LaunchedEffect(state.savedToken) { tokenDraft = state.savedToken }

    Scaffold(
        modifier = Modifier.testTag("fptn_settings"),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.fptn_settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FptnAboutCard()
            FptnTokenCard(
                tokenDraft = tokenDraft,
                tokenInvalid = state.tokenInvalid,
                onDraftChange = { tokenDraft = it },
                onSave = { viewModel.onTokenSave(tokenDraft) },
            )
            FptnBypassCard(
                selected = state.bypassMethod,
                onSelect = { viewModel.onBypassMethodChange(it) },
            )
            FptnSplitTunnelCard(onOpenSplitTunnel = onOpenSplitTunnel)
            FptnPermissionsCard(context = context)
            FptnServersCard(
                hasToken = state.hasToken,
                servers = state.servers,
                selectedServerName = state.selectedServerName,
                autoSelect = state.autoSelect,
                onAutoSelect = { viewModel.onAutoSelect() },
                onServerSelect = { viewModel.onServerSelect(it) },
            )
            OutlinedButton(
                onClick = { experimentalVisible = true },
                modifier = Modifier.fillMaxWidth().testTag("fptn_experimental_button"),
            ) {
                Text(stringResource(R.string.fptn_experimental_button))
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (experimentalVisible) {
        FptnExperimentalDialog(
            state = state,
            onReconnectNetwork = viewModel::onReconnectNetworkChange,
            onReconnectIp = viewModel::onReconnectIpChange,
            onMaxAttempts = viewModel::onMaxAttemptsChange,
            onPauseSeconds = viewModel::onPauseSecondsChange,
            onResetServer = viewModel::onResetServerChange,
            onDismiss = { experimentalVisible = false },
        )
    }
}

@Composable
private fun FptnAboutCard() {
    val uriHandler = LocalUriHandler.current
    SettingsCard(testTag = "fptn_about_card") {
        Text(
            text = stringResource(R.string.fptn_about_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(12.dp))
        FptnLink(
            label = stringResource(R.string.fptn_link_website),
            url = FptnLinks.WEBSITE,
            tag = "fptn_link_website",
            onClick = { uriHandler.openUri(FptnLinks.WEBSITE) },
        )
        FptnLink(
            label = stringResource(R.string.fptn_link_telegram),
            url = FptnLinks.TELEGRAM_GROUP,
            tag = "fptn_link_telegram",
            onClick = { uriHandler.openUri(FptnLinks.TELEGRAM_GROUP) },
        )
        FptnLink(
            label = stringResource(R.string.fptn_link_play),
            url = FptnLinks.PLAY,
            tag = "fptn_link_play",
            onClick = { uriHandler.openUri(FptnLinks.PLAY) },
        )
        FptnLink(
            label = stringResource(R.string.fptn_link_boosty),
            url = FptnLinks.BOOSTY,
            tag = "fptn_link_boosty",
            onClick = { uriHandler.openUri(FptnLinks.BOOSTY) },
        )
    }
}

@Composable
private fun FptnLink(label: String, url: String, tag: String, onClick: () -> Unit) {
    val annotated = buildAnnotatedString {
        append(label)
        append(" ")
        withStyle(
            SpanStyle(
                color = MaterialTheme.colorScheme.primary,
                textDecoration = TextDecoration.Underline,
            ),
        ) {
            append(url)
        }
    }
    Text(
        text = annotated,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp)
            .testTag(tag),
    )
}

@Composable
private fun FptnTokenCard(
    tokenDraft: String,
    tokenInvalid: Boolean,
    onDraftChange: (String) -> Unit,
    onSave: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    SettingsCard(testTag = "fptn_token_card") {
        CardHeader(icon = Icons.Filled.Refresh, title = stringResource(R.string.fptn_section_token))
        Text(
            text = stringResource(R.string.fptn_section_token_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
        OutlinedTextField(
            value = tokenDraft,
            onValueChange = onDraftChange,
            label = { Text(stringResource(R.string.fptn_token_hint)) },
            isError = tokenInvalid,
            supportingText = if (tokenInvalid) {
                { Text(stringResource(R.string.fptn_token_invalid)) }
            } else {
                null
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)
                .testTag("fptn_token_field"),
            singleLine = true,
        )
        TextButton(
            onClick = onSave,
            modifier = Modifier.fillMaxWidth().testTag("fptn_token_save"),
        ) {
            Text(stringResource(R.string.fptn_token_save))
        }
        Text(
            text = buildAnnotatedString {
                append(stringResource(R.string.fptn_bot_hint))
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .padding(top = 4.dp)
                .clickable { uriHandler.openUri(FptnLinks.BOT) }
                .testTag("fptn_bot_hint"),
        )
    }
}

@Composable
private fun FptnBypassCard(selected: FptnBypassMethod, onSelect: (FptnBypassMethod) -> Unit) {
    SettingsCard(testTag = "fptn_bypass_card") {
        CardHeader(icon = Icons.Filled.Lock, title = stringResource(R.string.fptn_section_bypass))
        Text(
            text = stringResource(R.string.fptn_section_bypass_summary),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
        )
        FptnBypassMethod.entries.forEach { method ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(selected = selected == method, onClick = { onSelect(method) })
                    .padding(vertical = 4.dp)
                    .testTag("fptn_bypass_${method.name.lowercase()}"),
            ) {
                RadioButton(selected = selected == method, onClick = { onSelect(method) })
                Text(
                    text = method.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun FptnSplitTunnelCard(onOpenSplitTunnel: () -> Unit) {
    SettingsCard(testTag = "fptn_split_tunnel_card", onClick = onOpenSplitTunnel) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            CardHeader(
                icon = Icons.Filled.Settings,
                title = stringResource(R.string.fptn_section_split_tunnel),
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = stringResource(R.string.fptn_section_split_tunnel_summary),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun FptnPermissionsCard(context: Context) {
    SettingsCard(testTag = "fptn_permissions_card") {
        CardHeader(
            icon = Icons.Filled.Info,
            title = stringResource(R.string.fptn_section_permissions),
        )
        Spacer(modifier = Modifier.height(8.dp))
        PermissionRow(
            label = stringResource(R.string.fptn_perm_battery),
            tag = "fptn_perm_battery",
            onClick = { openBatteryOptimizationSettings(context) },
        )
        PermissionRow(
            label = stringResource(R.string.fptn_perm_background),
            tag = "fptn_perm_background",
            onClick = { openDataUsageSettings(context) },
        )
    }
}

@Composable
private fun PermissionRow(label: String, tag: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp)
            .testTag(tag),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun FptnServersCard(
    hasToken: Boolean,
    servers: List<FptnServer>,
    selectedServerName: String?,
    autoSelect: Boolean,
    onAutoSelect: () -> Unit,
    onServerSelect: (String) -> Unit,
) {
    SettingsCard(testTag = "fptn_server_list") {
        CardHeader(icon = Icons.Filled.LocationOn, title = stringResource(R.string.fptn_section_servers))
        if (!hasToken) {
            Text(
                text = stringResource(R.string.fptn_no_token),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp).testTag("fptn_no_token"),
            )
            return@SettingsCard
        }
        Spacer(modifier = Modifier.height(8.dp))
        FptnServerRow(
            label = stringResource(R.string.fptn_server_auto),
            flag = "",
            selected = autoSelect,
            onClick = onAutoSelect,
            tag = "fptn_server_auto",
        )
        servers.forEach { server ->
            FptnServerRow(
                label = server.name,
                flag = countryFlag(server.countryCode),
                selected = !autoSelect && selectedServerName == server.name,
                onClick = { onServerSelect(server.name) },
                tag = "fptn_server_${server.name}",
            )
        }
    }
}

@Composable
private fun FptnServerRow(
    label: String,
    flag: String,
    selected: Boolean,
    onClick: () -> Unit,
    tag: String,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick)
            .padding(vertical = 4.dp)
            .testTag(tag),
    ) {
        RadioButton(selected = selected, onClick = null)
        if (flag.isNotEmpty()) {
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = flag, style = MaterialTheme.typography.bodyLarge)
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

@Composable
private fun FptnExperimentalDialog(
    state: FptnSettingsUiState,
    onReconnectNetwork: (Boolean) -> Unit,
    onReconnectIp: (Boolean) -> Unit,
    onMaxAttempts: (Int) -> Unit,
    onPauseSeconds: (Int) -> Unit,
    onResetServer: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.fptn_experimental_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.fptn_experimental_reconnect_header),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                FptnSwitchRow(
                    label = stringResource(R.string.fptn_reconnect_network),
                    checked = state.reconnectOnNetworkChange,
                    onCheckedChange = onReconnectNetwork,
                    tag = "fptn_exp_reconnect_network",
                )
                FptnSwitchRow(
                    label = stringResource(R.string.fptn_reconnect_ip),
                    checked = state.reconnectOnIpChange,
                    onCheckedChange = onReconnectIp,
                    tag = "fptn_exp_reconnect_ip",
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.fptn_experimental_attempts_header),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(R.string.fptn_max_attempts, state.maxReconnectAttempts),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Slider(
                    value = state.maxReconnectAttempts.toFloat(),
                    onValueChange = { onMaxAttempts(it.toInt()) },
                    valueRange = 1f..10f,
                    steps = 8,
                    modifier = Modifier.testTag("fptn_exp_max_attempts"),
                )
                Text(
                    text = stringResource(R.string.fptn_pause_seconds, state.reconnectPauseSeconds),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Slider(
                    value = state.reconnectPauseSeconds.toFloat(),
                    onValueChange = { onPauseSeconds(it.toInt()) },
                    valueRange = 1f..10f,
                    steps = 8,
                    modifier = Modifier.testTag("fptn_exp_pause_seconds"),
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.fptn_experimental_others_header),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                FptnSwitchRow(
                    label = stringResource(R.string.fptn_reset_server),
                    checked = state.resetServerOnDisconnect,
                    onCheckedChange = onResetServer,
                    tag = "fptn_exp_reset_server",
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("fptn_exp_save")) {
                Text(stringResource(R.string.fptn_experimental_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("fptn_exp_cancel")) {
                Text(stringResource(R.string.fptn_experimental_cancel))
            }
        },
    )
}

@Composable
private fun FptnSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    tag: String,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().testTag(tag),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SettingsCard(
    testTag: String,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val baseModifier = Modifier.fillMaxWidth().testTag(testTag)
    val clickModifier = if (onClick != null) baseModifier.clickable(onClick = onClick) else baseModifier
    Card(
        modifier = clickModifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            content()
        }
    }
}

@Composable
private fun CardHeader(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(18.dp),
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}

private fun openBatteryOptimizationSettings(context: Context) {
    val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}

private fun openDataUsageSettings(context: Context) {
    val intent = Intent(Settings.ACTION_DATA_USAGE_SETTINGS)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
        .onFailure {
            val fallback = Intent(Settings.ACTION_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(fallback) }
        }
}

private fun countryFlag(code: String): String {
    if (code.length != 2) return ""
    val upper = code.uppercase()
    val first = Character.codePointAt(upper, 0) - 'A'.code + 0x1F1E6
    val second = Character.codePointAt(upper, 1) - 'A'.code + 0x1F1E6
    return String(Character.toChars(first)) + String(Character.toChars(second))
}
