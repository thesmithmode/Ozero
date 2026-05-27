package ru.ozero.desktop.ui.settings.engines

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.ozero.desktop.ui.theme.OzeroPalette
import ru.ozero.desktop.vpn.DefaultWarpConfigPort
import ru.ozero.desktop.vpn.FilePickerPort
import ru.ozero.desktop.vpn.SwingFilePickerPort
import ru.ozero.desktop.vpn.WarpConfigPort
import ru.ozero.enginewarp.WarpConfParser
import ru.ozero.enginewarp.WarpEditDraft
import ru.ozero.enginewarp.WarpIniBuilder
import ru.ozero.enginewarp.draftFromConfig
import ru.ozero.enginewarp.emptyWarpDraft
import ru.ozero.enginewarp.hasRequiredFields
import ru.ozero.enginewarp.toWarpConfig
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WarpDesktopSettingsScreen(
    onBack: () -> Unit,
    configPort: WarpConfigPort = DefaultWarpConfigPort(),
    filePicker: FilePickerPort? = null,
) {
    val resolvedFilePicker = remember(filePicker) {
        filePicker ?: SwingFilePickerPort()
    }
    val initial = remember {
        loadInitialDraft(configPort)
    }
    var draft by remember { mutableStateOf(initial.draft) }
    var saved by remember { mutableStateOf(true) }
    var lastError by remember { mutableStateOf(initial.error) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("WARP") },
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
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(Modifier.height(4.dp))

            Text(
                text = "WireGuard profile",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )

            Text(
                text = "Same WARP fields as Android. The saved file is used by Windows start.",
                style = MaterialTheme.typography.bodySmall,
                color = OzeroPalette.Text3,
            )

            WarpConfigActions(
                hasConfig = hasRequiredFields(draft),
                onLoad = {
                    val selectedPath = resolvedFilePicker.pickConfigFile() ?: return@WarpConfigActions
                    runCatching {
                        draft = loadDraftFromText(File(selectedPath).readText()).getOrThrow()
                        saved = false
                        lastError = null
                    }.onFailure {
                        lastError = "Failed to load config: ${it.message}"
                    }
                },
                onClear = {
                    draft = emptyWarpDraft()
                    saved = false
                    runCatching { configPort.saveWarpConfigText("") }
                },
            )

            WarpDraftFields(
                draft = draft,
                onChange = {
                    draft = it
                    saved = false
                    lastError = null
                },
            )

            Button(
                onClick = {
                    saveDraft(configPort, draft).onSuccess {
                        saved = true
                        lastError = null
                    }.onFailure {
                        lastError = "Failed to save config: ${it.message}"
                    }
                },
                enabled = !saved,
            ) {
                Text("Save")
            }

            WarpSaveStatus(saved = saved, hasConfig = hasRequiredFields(draft), lastError = lastError)

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun WarpConfigActions(
    hasConfig: Boolean,
    onLoad: () -> Unit,
    onClear: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Button(onClick = onLoad) {
            Text("Load from file")
        }

        if (hasConfig) {
            OutlinedButton(onClick = onClear) {
                Text("Clear")
            }
        }
    }
}

@Composable
private fun WarpSaveStatus(saved: Boolean, hasConfig: Boolean, lastError: String?) {
    if (lastError == null && saved && hasConfig) {
        Text(
            text = "Config saved",
            style = MaterialTheme.typography.bodySmall,
            color = OzeroPalette.StateConnected,
        )
    }

    if (lastError != null) {
        Text(
            text = lastError,
            style = MaterialTheme.typography.bodySmall,
            color = OzeroPalette.StateDanger,
        )
    }
}

@Composable
private fun WarpDraftFields(
    draft: WarpEditDraft,
    onChange: (WarpEditDraft) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        WarpField("Name", draft.name) { onChange(draft.copy(name = it)) }
        WarpField("Endpoint", draft.endpoint) { onChange(draft.copy(endpoint = it)) }
        WarpField("Private key", draft.privateKey) { onChange(draft.copy(privateKey = it)) }
        WarpField("Public key", draft.publicKey) { onChange(draft.copy(publicKey = it)) }
        WarpField("Peer public key", draft.peerPublicKey) { onChange(draft.copy(peerPublicKey = it)) }
        WarpField("IPv4 address", draft.addressV4) { onChange(draft.copy(addressV4 = it)) }
        WarpField("IPv6 address", draft.addressV6) { onChange(draft.copy(addressV6 = it)) }
        WarpField("DNS", draft.dns) { onChange(draft.copy(dns = it)) }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            WarpField("MTU", draft.mtu, Modifier.weight(1f)) { onChange(draft.copy(mtu = it)) }
            WarpField("Keepalive", draft.keepalive, Modifier.weight(1f)) { onChange(draft.copy(keepalive = it)) }
        }
        WarpAwgFields(draft, onChange)
    }
}

@Composable
private fun WarpAwgFields(
    draft: WarpEditDraft,
    onChange: (WarpEditDraft) -> Unit,
) {
    Text(
        text = "AmneziaWG",
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        WarpField("Jc", draft.jc, Modifier.weight(1f)) { onChange(draft.copy(jc = it)) }
        WarpField("Jmin", draft.jmin, Modifier.weight(1f)) { onChange(draft.copy(jmin = it)) }
        WarpField("Jmax", draft.jmax, Modifier.weight(1f)) { onChange(draft.copy(jmax = it)) }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        WarpField("S1", draft.s1, Modifier.weight(1f)) { onChange(draft.copy(s1 = it)) }
        WarpField("S2", draft.s2, Modifier.weight(1f)) { onChange(draft.copy(s2 = it)) }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        WarpField("H1", draft.h1, Modifier.weight(1f)) { onChange(draft.copy(h1 = it)) }
        WarpField("H2", draft.h2, Modifier.weight(1f)) { onChange(draft.copy(h2 = it)) }
        WarpField("H3", draft.h3, Modifier.weight(1f)) { onChange(draft.copy(h3 = it)) }
        WarpField("H4", draft.h4, Modifier.weight(1f)) { onChange(draft.copy(h4 = it)) }
    }
}

@Composable
private fun WarpField(
    label: String,
    value: String,
    modifier: Modifier = Modifier.fillMaxWidth(),
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = modifier,
        singleLine = true,
        label = { Text(label) },
    )
}

private data class LoadedWarpDraft(
    val draft: WarpEditDraft,
    val error: String?,
)

private fun loadInitialDraft(configPort: WarpConfigPort): LoadedWarpDraft {
    val text = runCatching { configPort.loadWarpConfigText() ?: "" }.getOrDefault("")
    if (text.isBlank()) return LoadedWarpDraft(emptyWarpDraft(), null)
    return loadDraftFromText(text).fold(
        onSuccess = { LoadedWarpDraft(it, null) },
        onFailure = { LoadedWarpDraft(emptyWarpDraft(), "Failed to parse saved config: ${it.message}") },
    )
}

private fun loadDraftFromText(text: String): Result<WarpEditDraft> =
    WarpConfParser.parse(text).map { draftFromConfig(it) }

private fun saveDraft(configPort: WarpConfigPort, draft: WarpEditDraft): Result<Unit> {
    if (!hasRequiredFields(draft)) {
        return Result.failure(IllegalArgumentException("Required fields are empty"))
    }
    val configText = WarpIniBuilder.build(draft.toWarpConfig())
    return runCatching { configPort.saveWarpConfigText(configText) }
}
