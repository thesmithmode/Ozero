package ru.ozero.app.ui.settings.engines

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.io.ByteArrayInputStream
import ru.ozero.app.R
import ru.ozero.app.ui.theme.OzeroPalette
import androidx.compose.ui.text.font.FontFamily
import ru.ozero.enginewarp.AwgPreset
import ru.ozero.enginewarp.AwgPresets
import ru.ozero.enginewarp.DnsPreset
import ru.ozero.enginewarp.DnsPresets
import ru.ozero.enginewarp.EndpointPreset
import ru.ozero.enginewarp.EndpointPresets
import ru.ozero.enginewarp.WarpConfigSlot

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WarpEngineSettingsScreen(
    onBack: () -> Unit,
    viewModel: WarpEngineSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val cooldownRemainingMs by viewModel.cooldownRemainingMs.collectAsStateWithLifecycle()

    if (state.showTweaks && state.editDraft != null) {
        BackHandler { viewModel.onEditCancel() }
        WarpConnectionTweaksScreen(
            draft = state.editDraft!!,
            onDraftChange = viewModel::onEditDraftChange,
            onSave = viewModel::onSaveEdit,
            onCancel = viewModel::onEditCancel,
        )
        return
    }

    if (state.editDraft != null) {
        BackHandler { viewModel.onEditCancel() }
        WarpEditScreen(
            draft = state.editDraft!!,
            onDraftChange = viewModel::onEditDraftChange,
            onSave = viewModel::onSaveEdit,
            onCancel = viewModel::onEditCancel,
        )
        return
    }

    val context = LocalContext.current
    var infoDialogVisible by remember { mutableStateOf(false) }
    var importOverlayVisible by remember { mutableStateOf(false) }
    LaunchedEffect(state.importSuccessCount) {
        if (state.importSuccessCount > 0) {
            importOverlayVisible = true
            kotlinx.coroutines.delay(IMPORT_OVERLAY_HOLD_MS)
            importOverlayVisible = false
        }
    }
    val overlayAlpha by animateFloatAsState(
        targetValue = if (importOverlayVisible) 1f else 0f,
        animationSpec = tween(durationMillis = IMPORT_OVERLAY_FADE_MS),
        label = "importOverlay",
    )
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val displayName = context.contentResolver.query(
            uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null,
        )?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null } ?: "import"
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: return@rememberLauncherForActivityResult
        if (displayName.endsWith(".yaml", ignoreCase = true) || displayName.endsWith(".yml", ignoreCase = true)) {
            viewModel.onImportClashYaml(ByteArrayInputStream(bytes), displayName)
        } else {
            viewModel.onImportFile(ByteArrayInputStream(bytes))
        }
    }
    Scaffold(
        modifier = Modifier.testTag("warp_settings"),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.warp_settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(
                        onClick = { infoDialogVisible = true },
                        modifier = Modifier.testTag("warp_info_button"),
                    ) {
                        Icon(Icons.Filled.Info, contentDescription = stringResource(R.string.warp_info_button))
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            WarpScreenContent(
                state = state,
                cooldownRemainingMs = cooldownRemainingMs,
                modifier = Modifier.fillMaxSize(),
                onGenerate = viewModel::onGenerate,
                onCancelGenerate = viewModel::onCancelGenerate,
                onImportFile = { filePickerLauncher.launch("*/*") },
                onSetActive = viewModel::onSetActive,
                onStartEdit = viewModel::onStartEdit,
                onDeleteSlot = viewModel::onDeleteSlot,
                onOpenTweaks = viewModel::onStartTweaks,
            )
            if (overlayAlpha > 0f) {
                ImportSuccessOverlay(
                    modifier = Modifier.align(Alignment.Center).alpha(overlayAlpha),
                )
            }
            if (state.isProvingEndpoints) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(16.dp))
                        Text(state.provingProgress, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
    if (infoDialogVisible) {
        WarpInfoDialog(
            onDismiss = { infoDialogVisible = false },
            onLinkClick = { url ->
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { context.startActivity(intent) }
            },
        )
    }
}

@Composable
private fun WarpInfoDialog(onDismiss: () -> Unit, onLinkClick: (String) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.warp_info_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.warp_info_intro),
                    style = MaterialTheme.typography.bodySmall,
                )
                WARP_INFO_LINKS.forEach { url ->
                    TextButton(
                        onClick = { onLinkClick(url) },
                        modifier = Modifier.fillMaxWidth().testTag("warp_info_link_$url"),
                    ) {
                        Text(
                            text = url,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.warp_info_close))
            }
        },
    )
}

private val WARP_INFO_LINKS = listOf(
    "https://warp-generator.github.io/warp/",
    "https://ptechgithub.github.io/wg/",
    "https://warp-mirrors.vercel.app/",
)

@Composable
private fun ImportSuccessOverlay(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.size(IMPORT_OVERLAY_SIZE_DP.dp),
        shape = RectangleShape,
        color = Color.Transparent,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(IMPORT_OVERLAY_ICON_DP.dp),
            )
        }
    }
}

private const val IMPORT_OVERLAY_HOLD_MS = 900L
private const val IMPORT_OVERLAY_FADE_MS = 600
private const val IMPORT_OVERLAY_SIZE_DP = 120
private const val IMPORT_OVERLAY_ICON_DP = 96

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WarpEditScreen(
    draft: WarpEditDraft,
    onDraftChange: (WarpEditDraft) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.warp_edit_title)) },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    TextButton(onClick = onSave) {
                        Text(stringResource(R.string.warp_edit_save))
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
            WarpTextField(
                label = stringResource(R.string.warp_field_name),
                value = draft.name,
                onValueChange = { onDraftChange(draft.copy(name = it)) },
                tag = "warp_edit_name",
            )
            WarpInterfaceSection(draft, onDraftChange)
            WarpPeerSection(draft, onDraftChange)
            WarpAwgSection(draft, onDraftChange)
            WarpDoHSection(
                selected = draft.doHProvider,
                onSelect = { onDraftChange(draft.copy(doHProvider = it)) },
            )
        }
    }
}

@Composable
private fun WarpInterfaceSection(draft: WarpEditDraft, onDraftChange: (WarpEditDraft) -> Unit) {
    SectionLabel(stringResource(R.string.warp_section_interface))
    WarpTextField(
        label = stringResource(R.string.warp_field_private_key),
        value = draft.privateKey,
        onValueChange = { onDraftChange(draft.copy(privateKey = it)) },
        tag = "warp_edit_priv",
    )
    WarpTextField(
        label = stringResource(R.string.warp_field_public_key),
        value = draft.publicKey,
        onValueChange = { onDraftChange(draft.copy(publicKey = it)) },
        tag = "warp_edit_pub",
    )
    WarpTextField(
        label = stringResource(R.string.warp_field_address_v4),
        value = draft.addressV4,
        onValueChange = { onDraftChange(draft.copy(addressV4 = it)) },
        tag = "warp_edit_addr4",
    )
    WarpTextField(
        label = stringResource(R.string.warp_field_address_v6),
        value = draft.addressV6,
        onValueChange = { onDraftChange(draft.copy(addressV6 = it)) },
        tag = "warp_edit_addr6",
    )
    WarpDnsPresetsRow(onPresetClick = { preset ->
        onDraftChange(draft.copy(dns = preset.servers.joinToString(", ")))
    })
    WarpTextField(
        label = stringResource(R.string.warp_field_dns),
        value = draft.dns,
        onValueChange = { onDraftChange(draft.copy(dns = it)) },
        tag = "warp_edit_dns",
    )
    WarpTextField(
        label = stringResource(R.string.warp_field_mtu),
        value = draft.mtu,
        onValueChange = { onDraftChange(draft.copy(mtu = it)) },
        keyboardType = KeyboardType.Number,
        tag = "warp_edit_mtu",
    )
}

@Composable
private fun WarpPeerSection(draft: WarpEditDraft, onDraftChange: (WarpEditDraft) -> Unit) {
    SectionLabel(stringResource(R.string.warp_section_peer))
    WarpEndpointPresetsRow(onPresetClick = { preset ->
        onDraftChange(draft.copy(endpoint = preset.endpoint))
    })
    WarpTextField(
        label = stringResource(R.string.warp_field_endpoint),
        value = draft.endpoint,
        onValueChange = { onDraftChange(draft.copy(endpoint = it)) },
        tag = "warp_edit_endpoint",
    )
    WarpTextField(
        label = stringResource(R.string.warp_field_peer_public_key),
        value = draft.peerPublicKey,
        onValueChange = { onDraftChange(draft.copy(peerPublicKey = it)) },
        tag = "warp_edit_peer_pub",
    )
    WarpTextField(
        label = stringResource(R.string.warp_field_keepalive),
        value = draft.keepalive,
        onValueChange = { onDraftChange(draft.copy(keepalive = it)) },
        keyboardType = KeyboardType.Number,
        tag = "warp_edit_keepalive",
    )
}

@Composable
private fun WarpAwgSection(draft: WarpEditDraft, onDraftChange: (WarpEditDraft) -> Unit) {
    SectionLabel(stringResource(R.string.warp_section_awg))
    WarpAwgPresetsRow(onPresetClick = { preset ->
        onDraftChange(applyPreset(draft, preset))
    })
    WarpTextField(
        label = "Jc", value = draft.jc,
        onValueChange = { onDraftChange(draft.copy(jc = it)) },
        keyboardType = KeyboardType.Number, tag = "warp_edit_jc",
    )
    WarpTextField(
        label = "Jmin", value = draft.jmin,
        onValueChange = { onDraftChange(draft.copy(jmin = it)) },
        keyboardType = KeyboardType.Number, tag = "warp_edit_jmin",
    )
    WarpTextField(
        label = "Jmax", value = draft.jmax,
        onValueChange = { onDraftChange(draft.copy(jmax = it)) },
        keyboardType = KeyboardType.Number, tag = "warp_edit_jmax",
    )
    WarpTextField(
        label = "S1", value = draft.s1,
        onValueChange = { onDraftChange(draft.copy(s1 = it)) },
        keyboardType = KeyboardType.Number, tag = "warp_edit_s1",
    )
    WarpTextField(
        label = "S2", value = draft.s2,
        onValueChange = { onDraftChange(draft.copy(s2 = it)) },
        keyboardType = KeyboardType.Number, tag = "warp_edit_s2",
    )
    WarpTextField(
        label = "H1", value = draft.h1,
        onValueChange = { onDraftChange(draft.copy(h1 = it)) },
        keyboardType = KeyboardType.Number, tag = "warp_edit_h1",
    )
    WarpTextField(
        label = "H2", value = draft.h2,
        onValueChange = { onDraftChange(draft.copy(h2 = it)) },
        keyboardType = KeyboardType.Number, tag = "warp_edit_h2",
    )
    WarpTextField(
        label = "H3", value = draft.h3,
        onValueChange = { onDraftChange(draft.copy(h3 = it)) },
        keyboardType = KeyboardType.Number, tag = "warp_edit_h3",
    )
    WarpTextField(
        label = "H4", value = draft.h4,
        onValueChange = { onDraftChange(draft.copy(h4 = it)) },
        keyboardType = KeyboardType.Number, tag = "warp_edit_h4",
    )
}

@Composable
private fun WarpAwgPresetsRow(onPresetClick: (AwgPreset) -> Unit) {
    androidx.compose.foundation.lazy.LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().testTag("warp_awg_presets"),
    ) {
        items(AwgPresets.ALL) { preset ->
            androidx.compose.material3.AssistChip(
                onClick = { onPresetClick(preset) },
                label = { Text(preset.name) },
                modifier = Modifier.testTag("warp_awg_preset_${preset.id}"),
            )
        }
    }
}

@Composable
private fun WarpEndpointPresetsRow(onPresetClick: (EndpointPreset) -> Unit) {
    androidx.compose.foundation.lazy.LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().testTag("warp_endpoint_presets"),
    ) {
        items(EndpointPresets.ALL) { preset ->
            androidx.compose.material3.AssistChip(
                onClick = { onPresetClick(preset) },
                label = { Text(preset.name) },
                modifier = Modifier.testTag("warp_endpoint_preset_${preset.id}"),
            )
        }
    }
}

@Composable
private fun WarpDnsPresetsRow(onPresetClick: (DnsPreset) -> Unit) {
    androidx.compose.foundation.lazy.LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().testTag("warp_dns_presets"),
    ) {
        items(DnsPresets.ALL) { preset ->
            androidx.compose.material3.AssistChip(
                onClick = { onPresetClick(preset) },
                label = { Text(preset.name) },
                modifier = Modifier.testTag("warp_dns_preset_${preset.id}"),
            )
        }
    }
}

private fun applyPreset(draft: WarpEditDraft, preset: AwgPreset): WarpEditDraft {
    val p = preset.params
    return draft.copy(
        jc = p.junkPacketCount.toString(),
        jmin = p.junkPacketMinSize.toString(),
        jmax = p.junkPacketMaxSize.toString(),
        s1 = p.initPacketJunkSize.toString(),
        s2 = p.responsePacketJunkSize.toString(),
        h1 = p.initPacketMagicHeader.toString(),
        h2 = p.responsePacketMagicHeader.toString(),
        h3 = p.cookieReplyMagicHeader.toString(),
        h4 = p.transportMagicHeader.toString(),
    )
}

@Composable
private fun WarpDoHSection(
    selected: ru.ozero.enginewarp.DoHProvider,
    onSelect: (ru.ozero.enginewarp.DoHProvider) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().testTag("warp_doh_section"),
        colors = CardDefaults.cardColors(
            containerColor = OzeroPalette.Bg1,
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = stringResource(R.string.warp_doh_section_label),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            ru.ozero.enginewarp.DoHProvider.entries.forEach { provider ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = selected == provider,
                            onClick = { onSelect(provider) },
                        )
                        .testTag("warp_doh_${provider.name.lowercase()}"),
                ) {
                    RadioButton(
                        selected = selected == provider,
                        onClick = { onSelect(provider) },
                    )
                    Text(
                        text = provider.displayName,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun WarpTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text,
    tag: String = "",
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = Modifier.fillMaxWidth().testTag(tag),
    )
}

@Composable
private fun WarpScreenContent(
    state: WarpSettingsUiState,
    cooldownRemainingMs: Long,
    modifier: Modifier = Modifier,
    onGenerate: () -> Unit,
    onCancelGenerate: () -> Unit,
    onImportFile: () -> Unit,
    onSetActive: (String) -> Unit,
    onStartEdit: (String) -> Unit,
    onDeleteSlot: (String) -> Unit,
    onOpenTweaks: () -> Unit = {},
) {
    Box(modifier = modifier.fillMaxSize()) {
        if (state.slots.isEmpty()) {
            val progressLabel = state.progressMirror?.let {
                stringResource(R.string.warp_progress_mirror_fmt, it)
            }
            val resolvedError = state.errorMessageRes?.let { stringResource(it) } ?: state.errorMessage
            EmptyConfigCard(
                isRegistering = state.isRegistering,
                progressText = progressLabel,
                progressCurrent = state.progressCurrent,
                progressTotal = state.progressTotal,
                errorMessage = resolvedError,
                cooldownRemainingMs = cooldownRemainingMs,
                onGenerate = onGenerate,
                onCancelGenerate = onCancelGenerate,
                onImportFile = onImportFile,
                modifier = Modifier.padding(16.dp),
            )
        } else {
            SlotListContent(
                state = state,
                onSetActive = onSetActive,
                onStartEdit = onStartEdit,
                onDeleteSlot = onDeleteSlot,
            )
            if (state.isRegistering) {
                val mirrorText = state.progressMirror?.let {
                    stringResource(R.string.warp_progress_mirror_fmt, it)
                }
                WarpProgressCard(
                    progressText = mirrorText ?: stringResource(R.string.warp_registering),
                    progressCurrent = state.progressCurrent,
                    progressTotal = state.progressTotal,
                    onCancel = onCancelGenerate,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                )
            } else {
                WarpActionRow(
                    isRegistering = false,
                    cooldownRemainingMs = cooldownRemainingMs,
                    hasSlotsForTweaks = state.slots.isNotEmpty(),
                    onGenerate = onGenerate,
                    onCancelGenerate = onCancelGenerate,
                    onImportFile = onImportFile,
                    onOpenTweaks = onOpenTweaks,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }
}

@Composable
private fun WarpProgressCard(
    progressText: String,
    progressCurrent: Int,
    progressTotal: Int,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(progressText, style = MaterialTheme.typography.bodyMedium)
            if (progressTotal > 0) {
                LinearProgressIndicator(
                    progress = { progressCurrent.toFloat() / progressTotal },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.fillMaxWidth().testTag("warp_cancel_button"),
            ) {
                Text(stringResource(R.string.warp_cancel_generation))
            }
        }
    }
}

@Composable
private fun SlotListContent(
    state: WarpSettingsUiState,
    onSetActive: (String) -> Unit,
    onStartEdit: (String) -> Unit,
    onDeleteSlot: (String) -> Unit,
) {
    LazyColumn(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 16.dp, end = 16.dp, top = 8.dp, bottom = 80.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (state.errorMessage != null) {
            item {
                Text(
                    text = state.errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
        items(state.slots, key = { it.id }) { slot ->
            WarpConfigSlotCard(
                slot = slot,
                onSetActive = { onSetActive(slot.id) },
                onStartEdit = { onStartEdit(slot.id) },
                onDelete = { onDeleteSlot(slot.id) },
            )
        }
    }
}

@Composable
private fun WarpGenerateButton(
    isRegistering: Boolean,
    onGenerate: () -> Unit,
    onCancelGenerate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = if (isRegistering) onCancelGenerate else onGenerate,
        modifier = modifier.testTag("warp_generate_button"),
    ) {
        if (isRegistering) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp).padding(end = 8.dp),
                    strokeWidth = 2.dp,
                )
                Text(stringResource(R.string.warp_cancel_generation))
            }
        } else {
            Text(stringResource(R.string.warp_generate))
        }
    }
}

@Composable
private fun WarpActionRow(
    isRegistering: Boolean,
    cooldownRemainingMs: Long = 0L,
    hasSlotsForTweaks: Boolean = false,
    onGenerate: () -> Unit,
    onCancelGenerate: () -> Unit,
    onImportFile: () -> Unit,
    onOpenTweaks: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (cooldownRemainingMs > 0) {
            Text(
                text = stringResource(R.string.warp_next_generation_in_fmt, formatCooldown(cooldownRemainingMs)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            WarpGenerateButton(
                isRegistering = isRegistering,
                onGenerate = onGenerate,
                onCancelGenerate = onCancelGenerate,
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(
                onClick = onImportFile,
                enabled = !isRegistering,
                modifier = Modifier.weight(1f).testTag("warp_import_button"),
            ) {
                Text(stringResource(R.string.warp_import_file))
            }
        }
        if (hasSlotsForTweaks) {
            OutlinedButton(
                onClick = onOpenTweaks,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 8.dp)
                    .testTag("warp_tweaks_button"),
            ) {
                Text(stringResource(R.string.warp_tweaks_button))
            }
        }
    }
}

@Composable
private fun EmptyConfigCard(
    isRegistering: Boolean,
    progressText: String?,
    progressCurrent: Int,
    progressTotal: Int,
    errorMessage: String?,
    cooldownRemainingMs: Long = 0L,
    onGenerate: () -> Unit,
    onCancelGenerate: () -> Unit,
    onImportFile: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.warp_no_config),
                style = MaterialTheme.typography.titleMedium,
            )
            if (errorMessage != null) {
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (cooldownRemainingMs > 0) {
                Text(
                    text = stringResource(R.string.warp_next_generation_in_fmt, formatCooldown(cooldownRemainingMs)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (isRegistering) {
                WarpProgressCard(
                    progressText = progressText ?: stringResource(R.string.warp_registering),
                    progressCurrent = progressCurrent,
                    progressTotal = progressTotal,
                    onCancel = onCancelGenerate,
                )
            } else {
                Button(
                    onClick = onGenerate,
                    modifier = Modifier.fillMaxWidth().testTag("warp_generate_button"),
                ) {
                    Text(stringResource(R.string.warp_generate))
                }
                OutlinedButton(
                    onClick = onImportFile,
                    modifier = Modifier.fillMaxWidth().testTag("warp_import_button_empty"),
                ) {
                    Text(stringResource(R.string.warp_import_file))
                }
            }
        }
    }
}

private fun formatCooldown(ms: Long): String {
    val s = ms / 1000
    return "${s / 60}:${(s % 60).toString().padStart(2, '0')}"
}

@Composable
private fun WarpConfigSlotCard(
    slot: WarpConfigSlot,
    onSetActive: () -> Unit,
    onStartEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = OzeroPalette.Bg1,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .selectable(selected = slot.isActive, onClick = onSetActive)
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(
                selected = slot.isActive,
                onClick = null,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(text = slot.name, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = slot.config.peerEndpoint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace,
                )
                if (slot.endpointList.size > 1) {
                    Text(
                        text = stringResource(R.string.warp_endpoint_count, slot.endpointList.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            IconButton(onClick = onStartEdit) {
                Icon(Icons.Filled.Edit, contentDescription = null)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = null)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WarpConnectionTweaksScreen(
    draft: WarpEditDraft,
    onDraftChange: (WarpEditDraft) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.warp_tweaks_title)) },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    TextButton(onClick = onSave) {
                        Text(stringResource(R.string.warp_edit_save))
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
            WarpTweaksEndpointSection(draft, onDraftChange)
            WarpTweaksAwgSection(draft, onDraftChange)
            WarpDoHSection(
                selected = draft.doHProvider,
                onSelect = { onDraftChange(draft.copy(doHProvider = it)) },
            )
        }
    }
}

@Composable
private fun WarpTweaksEndpointSection(draft: WarpEditDraft, onDraftChange: (WarpEditDraft) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().testTag("warp_tweaks_endpoint"),
        colors = CardDefaults.cardColors(
            containerColor = OzeroPalette.Bg1,
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = stringResource(R.string.warp_tweaks_endpoint_section),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            EndpointPresets.ALL.forEach { preset ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = draft.endpoint == preset.endpoint,
                            onClick = { onDraftChange(draft.copy(endpoint = preset.endpoint)) },
                        )
                        .testTag("warp_tweaks_ep_${preset.id}"),
                ) {
                    RadioButton(
                        selected = draft.endpoint == preset.endpoint,
                        onClick = { onDraftChange(draft.copy(endpoint = preset.endpoint)) },
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = preset.name, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = preset.endpoint,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WarpTweaksAwgSection(draft: WarpEditDraft, onDraftChange: (WarpEditDraft) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().testTag("warp_tweaks_awg"),
        colors = CardDefaults.cardColors(
            containerColor = OzeroPalette.Bg1,
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = stringResource(R.string.warp_tweaks_awg_section),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            AwgPresets.ALL.forEach { preset ->
                val p = preset.params
                val isSelected = draft.jc == p.junkPacketCount.toString() &&
                    draft.jmin == p.junkPacketMinSize.toString() &&
                    draft.jmax == p.junkPacketMaxSize.toString()
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = isSelected,
                            onClick = { onDraftChange(applyPreset(draft, preset)) },
                        )
                        .testTag("warp_tweaks_awg_${preset.id}"),
                ) {
                    RadioButton(
                        selected = isSelected,
                        onClick = { onDraftChange(applyPreset(draft, preset)) },
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = preset.name, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = "Jc=${p.junkPacketCount} Jmin=${p.junkPacketMinSize} Jmax=${p.junkPacketMaxSize}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            }
        }
    }
}
