package ru.ozero.app.ui.settings.engines

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
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
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
import ru.ozero.enginefptn.FptnConfig
import ru.ozero.enginefptn.FptnServer

private object FptnLinks {
    const val BOT = "https://t.me/fptn_bot"
}

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
                sniDomain = state.sniDomain,
                onSelect = { viewModel.onBypassMethodChange(it) },
                onSniDomainChange = { viewModel.onSniDomainChange(it) },
            )
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
            onSave = { network, ip, maxAttempts, pauseSeconds, resetServer ->
                viewModel.onReconnectNetworkChange(network)
                viewModel.onReconnectIpChange(ip)
                viewModel.onMaxAttemptsChange(maxAttempts)
                viewModel.onPauseSecondsChange(pauseSeconds)
                viewModel.onResetServerChange(resetServer)
            },
            onDismiss = { experimentalVisible = false },
        )
    }
}

@Composable
private fun FptnAboutCard() {
    SettingsCard(testTag = "fptn_about_card") {
        Text(
            text = stringResource(R.string.fptn_about_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
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
        val botHint = stringResource(R.string.fptn_bot_hint)
        val linkSpan = SpanStyle(
            color = MaterialTheme.colorScheme.primary,
            textDecoration = TextDecoration.Underline,
        )
        Text(
            text = buildAnnotatedString {
                val linkText = "t.me/fptn_bot"
                val idx = botHint.indexOf(linkText)
                if (idx >= 0) {
                    append(botHint.substring(0, idx))
                    withStyle(linkSpan) { append(linkText) }
                    if (idx + linkText.length < botHint.length) {
                        append(botHint.substring(idx + linkText.length))
                    }
                } else {
                    withStyle(linkSpan) { append(botHint) }
                }
            },
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier
                .padding(top = 4.dp)
                .clickable { uriHandler.openUri(FptnLinks.BOT) }
                .testTag("fptn_bot_hint"),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FptnBypassCard(
    selected: FptnBypassMethod,
    sniDomain: String,
    onSelect: (FptnBypassMethod) -> Unit,
    onSniDomainChange: (String) -> Unit,
) {
    var showSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var sniDraft by remember(sniDomain) { mutableStateOf(sniDomain) }
    val scrollState = rememberScrollState()

    SettingsCard(testTag = "fptn_bypass_card", onClick = { showSheet = true }) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            CardHeader(
                icon = Icons.Filled.Lock,
                title = stringResource(R.string.fptn_section_bypass),
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = if (selected.usesSni) "${selected.displayName} · $sniDomain" else selected.displayName,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }

    if (showSheet) {
        val isReality = selected.isReality
        ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            sheetState = sheetState,
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 24.dp)
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = selected == FptnBypassMethod.SNI,
                            onClick = { onSelect(FptnBypassMethod.SNI) },
                        )
                        .padding(end = 16.dp, top = 4.dp, bottom = 4.dp)
                        .testTag("fptn_bypass_sni"),
                ) {
                    RadioButton(
                        selected = selected == FptnBypassMethod.SNI,
                        onClick = { onSelect(FptnBypassMethod.SNI) },
                    )
                    Text(
                        text = stringResource(R.string.fptn_bypass_sni_label),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = selected == FptnBypassMethod.OBFUSCATION,
                            onClick = { onSelect(FptnBypassMethod.OBFUSCATION) },
                        )
                        .padding(end = 16.dp, top = 4.dp, bottom = 4.dp)
                        .testTag("fptn_bypass_obfuscation"),
                ) {
                    RadioButton(
                        selected = selected == FptnBypassMethod.OBFUSCATION,
                        onClick = { onSelect(FptnBypassMethod.OBFUSCATION) },
                    )
                    Text(
                        text = stringResource(R.string.fptn_bypass_obfuscation_label),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = isReality,
                            onClick = { if (!isReality) onSelect(FptnBypassMethod.DEFAULT_REALITY) },
                        )
                        .padding(end = 16.dp, top = 4.dp, bottom = 4.dp)
                        .testTag("fptn_bypass_reality"),
                ) {
                    RadioButton(
                        selected = isReality,
                        onClick = { if (!isReality) onSelect(FptnBypassMethod.DEFAULT_REALITY) },
                    )
                    Text(
                        text = stringResource(R.string.fptn_bypass_reality_label),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                if (isReality) {
                    Text(
                        text = stringResource(R.string.fptn_bypass_reality_variant),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 40.dp, top = 8.dp),
                    )
                    FptnBypassMethod.REALITY_METHODS.forEach { method ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = selected == method,
                                    onClick = { onSelect(method) },
                                )
                                .padding(start = 32.dp, end = 16.dp, top = 2.dp, bottom = 2.dp)
                                .testTag("fptn_bypass_reality_${method.name.lowercase()}"),
                        ) {
                            RadioButton(selected = selected == method, onClick = { onSelect(method) })
                            Text(
                                text = method.displayName,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                    }
                    Text(
                        text = stringResource(R.string.fptn_bypass_reality_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(start = 40.dp, top = 4.dp, bottom = 4.dp),
                    )
                }
                if (selected.usesSni) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.fptn_section_sni),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = stringResource(R.string.fptn_sni_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    OutlinedTextField(
                        value = sniDraft,
                        onValueChange = { sniDraft = it },
                        label = { Text(stringResource(R.string.fptn_sni_hint)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .testTag("fptn_sni_field"),
                        singleLine = true,
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = {
                                sniDraft = FptnConfig.DEFAULT_SNI_DOMAIN
                                onSniDomainChange(FptnConfig.DEFAULT_SNI_DOMAIN)
                            },
                            modifier = Modifier.weight(1f).testTag("fptn_sni_reset"),
                        ) {
                            Text(stringResource(R.string.fptn_sni_reset))
                        }
                        TextButton(
                            onClick = {
                                val trimmed = sniDraft.trim()
                                if (trimmed.isNotBlank()) onSniDomainChange(trimmed)
                            },
                            modifier = Modifier.testTag("fptn_sni_save"),
                        ) {
                            Text(stringResource(R.string.fptn_sni_save))
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    onClick = { showSheet = false },
                    modifier = Modifier.fillMaxWidth().testTag("fptn_close"),
                ) {
                    Text(stringResource(R.string.fptn_close))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FptnServersCard(
    hasToken: Boolean,
    servers: List<FptnServer>,
    selectedServerName: String?,
    autoSelect: Boolean,
    onAutoSelect: () -> Unit,
    onServerSelect: (String) -> Unit,
) {
    var showSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val currentLabel = when {
        !hasToken -> stringResource(R.string.fptn_no_token)
        autoSelect -> stringResource(R.string.fptn_server_auto)
        else -> selectedServerName ?: stringResource(R.string.fptn_server_auto)
    }

    SettingsCard(
        testTag = "fptn_server_list",
        onClick = if (hasToken) ({ showSheet = true }) else null,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            CardHeader(
                icon = Icons.Filled.LocationOn,
                title = stringResource(R.string.fptn_section_servers),
                modifier = Modifier.weight(1f),
            )
            if (hasToken) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            text = currentLabel,
            style = MaterialTheme.typography.bodySmall,
            color = if (!hasToken) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.padding(top = 4.dp).testTag("fptn_no_token"),
        )
    }

    if (showSheet && hasToken) {
        ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            sheetState = sheetState,
        ) {
            FptnServerRow(
                label = stringResource(R.string.fptn_server_auto),
                flag = "",
                selected = autoSelect,
                onClick = {
                    onAutoSelect()
                    showSheet = false
                },
                tag = "fptn_server_auto",
            )
            servers.forEach { server ->
                FptnServerRow(
                    label = server.name,
                    flag = countryFlag(server.countryCode),
                    selected = !autoSelect && selectedServerName == server.name,
                    onClick = {
                        onServerSelect(server.name)
                        showSheet = false
                    },
                    tag = "fptn_server_${server.name}",
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
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
            .padding(horizontal = 16.dp, vertical = 4.dp)
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
    onSave: (
        reconnectNetwork: Boolean,
        reconnectIp: Boolean,
        maxAttempts: Int,
        pauseSeconds: Int,
        resetServer: Boolean,
    ) -> Unit,
    onDismiss: () -> Unit,
) {
    var reconnectNetwork by remember { mutableStateOf(state.reconnectOnNetworkChange) }
    var reconnectIp by remember { mutableStateOf(state.reconnectOnIpChange) }
    var maxAttempts by remember { mutableStateOf(state.maxReconnectAttempts) }
    var pauseSeconds by remember { mutableStateOf(state.reconnectPauseSeconds) }
    var resetServer by remember { mutableStateOf(state.resetServerOnDisconnect) }

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
                    checked = reconnectNetwork,
                    onCheckedChange = { reconnectNetwork = it },
                    tag = "fptn_exp_reconnect_network",
                )
                FptnSwitchRow(
                    label = stringResource(R.string.fptn_reconnect_ip),
                    checked = reconnectIp,
                    onCheckedChange = { reconnectIp = it },
                    tag = "fptn_exp_reconnect_ip",
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.fptn_experimental_attempts_header),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(R.string.fptn_max_attempts, maxAttempts),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Slider(
                    value = maxAttempts.toFloat(),
                    onValueChange = { maxAttempts = it.toInt() },
                    valueRange = 1f..10f,
                    steps = 8,
                    modifier = Modifier.testTag("fptn_exp_max_attempts"),
                )
                Text(
                    text = stringResource(R.string.fptn_pause_seconds, pauseSeconds),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Slider(
                    value = pauseSeconds.toFloat(),
                    onValueChange = { pauseSeconds = it.toInt() },
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
                    checked = resetServer,
                    onCheckedChange = { resetServer = it },
                    tag = "fptn_exp_reset_server",
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(reconnectNetwork, reconnectIp, maxAttempts, pauseSeconds, resetServer)
                    onDismiss()
                },
                modifier = Modifier.testTag("fptn_exp_save"),
            ) {
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

private fun countryFlag(code: String): String {
    if (code.length != 2) return ""
    val upper = code.uppercase()
    val first = Character.codePointAt(upper, 0) - 'A'.code + 0x1F1E6
    val second = Character.codePointAt(upper, 1) - 'A'.code + 0x1F1E6
    return String(Character.toChars(first)) + String(Character.toChars(second))
}
