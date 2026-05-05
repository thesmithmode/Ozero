package ru.ozero.app.ui.settings.engines

import java.io.ByteArrayInputStream
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.ozero.app.R
import ru.ozero.enginewarp.WarpConfigSlot

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WarpEngineSettingsScreen(
    onBack: () -> Unit,
    viewModel: WarpEngineSettingsViewModel = hiltViewModel(),
) {
    BackHandler(onBack = onBack)
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val importedMessage = stringResource(R.string.warp_imported)
    LaunchedEffect(state.importSuccess) {
        if (state.importSuccess) {
            snackbarHostState.showSnackbar(importedMessage)
            viewModel.onImportSuccessConsumed()
        }
    }
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: return@rememberLauncherForActivityResult
        viewModel.onImportFile(ByteArrayInputStream(bytes))
    }
    Scaffold(
        modifier = Modifier.testTag("warp_settings"),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.warp_settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        WarpScreenContent(
            state = state,
            modifier = Modifier.padding(padding),
            onGenerate = viewModel::onGenerate,
            onCancelGenerate = viewModel::onCancelGenerate,
            onImportFile = { filePickerLauncher.launch("*/*") },
            onSetActive = viewModel::onSetActive,
            onStartRename = viewModel::onStartRename,
            onDeleteSlot = viewModel::onDeleteSlot,
        )
        if (state.showRenameDialog) {
            WarpRenameDialog(
                text = state.renameText,
                onTextChange = viewModel::onRenameTextChange,
                onConfirm = viewModel::onRenameConfirm,
                onDismiss = viewModel::onRenameCancel,
            )
        }
    }
}

@Composable
private fun WarpScreenContent(
    state: WarpSettingsUiState,
    modifier: Modifier = Modifier,
    onGenerate: () -> Unit,
    onCancelGenerate: () -> Unit,
    onImportFile: () -> Unit,
    onSetActive: (String) -> Unit,
    onStartRename: (String) -> Unit,
    onDeleteSlot: (String) -> Unit,
) {
    Box(modifier = modifier.fillMaxSize()) {
        if (state.slots.isEmpty()) {
            EmptyConfigCard(
                isRegistering = state.isRegistering,
                progressText = state.progressText,
                errorMessage = state.errorMessage,
                onGenerate = onGenerate,
                onCancelGenerate = onCancelGenerate,
                onImportFile = onImportFile,
                modifier = Modifier.padding(16.dp),
            )
        } else {
            SlotListContent(
                state = state,
                onSetActive = onSetActive,
                onStartRename = onStartRename,
                onDeleteSlot = onDeleteSlot,
            )
            WarpActionRow(
                isRegistering = state.isRegistering,
                onGenerate = onGenerate,
                onCancelGenerate = onCancelGenerate,
                onImportFile = onImportFile,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

@Composable
private fun SlotListContent(
    state: WarpSettingsUiState,
    onSetActive: (String) -> Unit,
    onStartRename: (String) -> Unit,
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
        if (state.isRegistering && state.progressText != null) {
            item {
                Text(
                    text = state.progressText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
        items(state.slots, key = { it.id }) { slot ->
            WarpConfigSlotCard(
                slot = slot,
                onSetActive = { onSetActive(slot.id) },
                onStartRename = { onStartRename(slot.id) },
                onDelete = { onDeleteSlot(slot.id) },
            )
        }
    }
}

@Composable
private fun WarpActionRow(
    isRegistering: Boolean,
    onGenerate: () -> Unit,
    onCancelGenerate: () -> Unit,
    onImportFile: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Button(
            onClick = if (isRegistering) onCancelGenerate else onGenerate,
            modifier = Modifier.weight(1f).testTag("warp_generate_button"),
        ) {
            if (isRegistering) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(end = 8.dp),
                        strokeWidth = 2.dp,
                    )
                    Text(stringResource(R.string.warp_cancel_generation))
                }
            } else {
                Text(stringResource(R.string.warp_generate))
            }
        }
        OutlinedButton(
            onClick = onImportFile,
            enabled = !isRegistering,
            modifier = Modifier.weight(1f).testTag("warp_import_button"),
        ) {
            Text(stringResource(R.string.warp_import_file))
        }
    }
}

@Composable
private fun WarpRenameDialog(
    text: String,
    onTextChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.warp_rename_title)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                singleLine = true,
            )
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.warp_rename_ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.warp_rename_cancel)) } },
    )
}

@Composable
private fun EmptyConfigCard(
    isRegistering: Boolean,
    progressText: String?,
    errorMessage: String?,
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
            Button(
                onClick = if (isRegistering) onCancelGenerate else onGenerate,
                modifier = Modifier.fillMaxWidth().testTag("warp_generate_button"),
            ) {
                if (isRegistering) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(end = 8.dp),
                            strokeWidth = 2.dp,
                        )
                        Text(stringResource(R.string.warp_cancel_generation))
                    }
                } else {
                    Text(stringResource(R.string.warp_generate))
                }
            }
            if (isRegistering && progressText != null) {
                Text(
                    text = progressText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedButton(
                onClick = onImportFile,
                enabled = !isRegistering,
                modifier = Modifier.fillMaxWidth().testTag("warp_import_button"),
            ) {
                Text(stringResource(R.string.warp_import_file))
            }
        }
    }
}

@Composable
private fun WarpConfigSlotCard(
    slot: WarpConfigSlot,
    onSetActive: () -> Unit,
    onStartRename: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(
                selected = slot.isActive,
                onClick = onSetActive,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(text = slot.name, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = slot.config.peerEndpoint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onStartRename) {
                Icon(Icons.Filled.Edit, contentDescription = null)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = null)
            }
        }
    }
}
