package ru.ozero.app.ui.settings.engines

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.ozero.app.R
import ru.ozero.enginemasque.MasqueTransport

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MasqueSettingsScreen(onBack: () -> Unit, viewModel: MasqueSettingsViewModel = hiltViewModel()) {
    BackHandler(onBack = onBack)
    val state by viewModel.state.collectAsStateWithLifecycle()
    var consent by remember { mutableStateOf(false) }
    var clear by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.masque_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.settings_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            MasqueAccountSection(state, onRegister = { consent = true }, onClear = { clear = true })
            HorizontalDivider()
            MasqueTransportSection(state, viewModel::selectTransport, viewModel::setAutoEnabled)
        }
    }
    if (consent) {
        MasqueConsentDialog(onDismiss = { consent = false }, onAccept = {
            consent = false
            viewModel.register()
        })
    }
    if (clear) {
        AlertDialog(
            onDismissRequest = { clear = false },
            title = { Text(stringResource(R.string.masque_clear)) },
            text = { Text(stringResource(R.string.masque_clear_warning)) },
            confirmButton = {
                TextButton(onClick = {
                    clear = false
                    viewModel.clear()
                }) { Text(stringResource(R.string.masque_clear)) }
            },
            dismissButton = {
                TextButton(onClick = { clear = false }) { Text(stringResource(R.string.masque_cancel)) }
            },
        )
    }
}

@Composable
private fun MasqueAccountSection(state: MasqueSettingsUiState, onRegister: () -> Unit, onClear: () -> Unit) {
    Text(stringResource(R.string.masque_summary), style = MaterialTheme.typography.bodyMedium)
    Text(stringResource(if (state.registered) R.string.masque_registered else R.string.masque_not_registered))
    if (state.busy) Text(stringResource(R.string.masque_working))
    if (state.error) Text(stringResource(R.string.masque_error), color = MaterialTheme.colorScheme.error)
    val enabled = state.loaded && !state.busy
    Button(onClick = onRegister, enabled = enabled && !state.registered) {
        Text(stringResource(R.string.masque_register))
    }
    OutlinedButton(onClick = onClear, enabled = enabled && (state.registered || state.error)) {
        Text(stringResource(R.string.masque_clear))
    }
}

@Composable
private fun MasqueTransportSection(
    state: MasqueSettingsUiState,
    onTransport: (MasqueTransport) -> Unit,
    onAuto: (Boolean) -> Unit,
) {
    val enabled = state.loaded && !state.busy
    Text(stringResource(R.string.masque_transport), style = MaterialTheme.typography.titleMedium)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MasqueTransport.entries.forEach { transport ->
            FilterChip(
                selected = state.transport == transport,
                onClick = { onTransport(transport) },
                label = {
                    val label = when (transport) {
                        MasqueTransport.AUTO -> stringResource(R.string.masque_auto)
                        MasqueTransport.H3 -> "HTTP/3"
                        MasqueTransport.H2 -> "HTTP/2"
                    }
                    Text(label)
                },
                enabled = enabled,
            )
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(stringResource(R.string.masque_auto_enabled), modifier = Modifier.weight(1f))
        Switch(checked = state.autoEnabled, onCheckedChange = onAuto, enabled = enabled)
    }
}

@Composable
private fun MasqueConsentDialog(onDismiss: () -> Unit, onAccept: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.masque_register)) },
        text = {
            Column {
                Text(stringResource(R.string.masque_consent))
                TextButton(onClick = { uriHandler.openUri("https://www.cloudflare.com/application/terms/") }) {
                    Text(stringResource(R.string.masque_terms))
                }
            }
        },
        confirmButton = { TextButton(onClick = onAccept) { Text(stringResource(R.string.masque_accept)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.masque_cancel)) } },
    )
}
