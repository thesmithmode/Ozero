package ru.ozero.app.ui.settings.engines

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.ozero.app.R
import ru.ozero.enginefptn.FptnBypassMethod
import ru.ozero.enginefptn.FptnServer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FptnEngineSettingsScreen(
    onBack: () -> Unit,
    viewModel: FptnEngineSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
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
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FptnSectionLabel(stringResource(R.string.fptn_section_token))
            OutlinedTextField(
                value = tokenDraft,
                onValueChange = { tokenDraft = it },
                label = { Text(stringResource(R.string.fptn_token_hint)) },
                isError = state.tokenInvalid,
                supportingText = if (state.tokenInvalid) {
                    { Text(stringResource(R.string.fptn_token_invalid)) }
                } else null,
                modifier = Modifier.fillMaxWidth().testTag("fptn_token_field"),
                singleLine = true,
            )
            TextButton(
                onClick = { viewModel.onTokenSave(tokenDraft) },
                modifier = Modifier.fillMaxWidth().testTag("fptn_token_save"),
            ) {
                Text(stringResource(R.string.fptn_token_save))
            }
            Text(
                text = stringResource(R.string.fptn_bot_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp),
            )

            FptnSectionLabel(stringResource(R.string.fptn_section_servers))
            FptnServerSection(
                hasToken = state.hasToken,
                servers = state.servers,
                selectedServerName = state.selectedServerName,
                autoSelect = state.autoSelect,
                onAutoSelect = { viewModel.onAutoSelect() },
                onServerSelect = { viewModel.onServerSelect(it) },
            )

            FptnSectionLabel(stringResource(R.string.fptn_section_bypass))
            FptnBypassSection(
                selected = state.bypassMethod,
                onSelect = { viewModel.onBypassMethodChange(it) },
            )

            OutlinedButton(
                onClick = { experimentalVisible = true },
                modifier = Modifier.fillMaxWidth().testTag("fptn_experimental_button"),
            ) {
                Text(stringResource(R.string.fptn_experimental_button))
            }
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
private fun FptnServerSection(
    hasToken: Boolean,
    servers: List<FptnServer>,
    selectedServerName: String?,
    autoSelect: Boolean,
    onAutoSelect: () -> Unit,
    onServerSelect: (String) -> Unit,
) {
    if (!hasToken) {
        Text(
            text = stringResource(R.string.fptn_no_token),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag("fptn_no_token"),
        )
        return
    }
    Card(modifier = Modifier.fillMaxWidth().testTag("fptn_server_list")) {
        Column(modifier = Modifier.padding(4.dp)) {
            FptnServerRow(
                label = stringResource(R.string.fptn_server_auto),
                selected = autoSelect,
                onClick = onAutoSelect,
                tag = "fptn_server_auto",
            )
            servers.forEach { server ->
                FptnServerRow(
                    label = "${countryFlag(server.countryCode)} ${server.name}",
                    selected = !autoSelect && selectedServerName == server.name,
                    onClick = { onServerSelect(server.name) },
                    tag = "fptn_server_${server.name}",
                )
            }
        }
    }
}

@Composable
private fun FptnServerRow(label: String, selected: Boolean, onClick: () -> Unit, tag: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .testTag(tag),
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

@Composable
private fun FptnBypassSection(selected: FptnBypassMethod, onSelect: (FptnBypassMethod) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().testTag("fptn_bypass_section")) {
        Column(modifier = Modifier.padding(4.dp)) {
            FptnBypassMethod.entries.forEach { method ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(selected = selected == method, onClick = { onSelect(method) })
                        .padding(horizontal = 8.dp, vertical = 4.dp)
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
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                FptnSwitchRow(
                    label = stringResource(R.string.fptn_reset_server),
                    checked = state.resetServerOnDisconnect,
                    onCheckedChange = onResetServer,
                    tag = "fptn_exp_reset_server",
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("fptn_exp_close")) {
                Text(stringResource(R.string.fptn_close))
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
private fun FptnSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp),
    )
}

private fun countryFlag(code: String): String {
    if (code.length != 2) return ""
    val upper = code.uppercase()
    val first = Character.codePointAt(upper, 0) - 'A'.code + 0x1F1E6
    val second = Character.codePointAt(upper, 1) - 'A'.code + 0x1F1E6
    return String(Character.toChars(first)) + String(Character.toChars(second))
}
