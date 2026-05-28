package ru.ozero.app.ui.backup

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.ozero.app.R
import ru.ozero.corebackup.BackupCategory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(
    onBack: () -> Unit,
    viewModel: BackupViewModel = hiltViewModel(),
    importOnly: Boolean = false,
    showTopBar: Boolean = true,
    showWarning: Boolean = true,
    confirmImportImmediately: Boolean = false,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }

    val exportSuccessMsg = stringResource(R.string.backup_export_success)
    val importSuccessMsg = stringResource(R.string.backup_import_success)
    val errorPrefix = stringResource(R.string.backup_error, "")
    val filename = stringResource(R.string.backup_filename)

    var showExportDialog by rememberSaveable { mutableStateOf(false) }
    var pendingExportCategories by remember { mutableStateOf<Set<BackupCategory>>(emptySet()) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri: Uri? ->
        val cats = pendingExportCategories
        pendingExportCategories = emptySet()
        if (uri != null && cats.isNotEmpty()) viewModel.export(context, uri, cats)
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) viewModel.beginImport(context, uri)
    }

    LaunchedEffect(state) {
        when (state) {
            is BackupUiState.ExportSuccess -> {
                snackbar.showSnackbar(exportSuccessMsg)
                viewModel.dismissResult()
            }
            is BackupUiState.ImportSuccess -> {
                snackbar.showSnackbar(importSuccessMsg)
                viewModel.dismissResult()
            }
            is BackupUiState.Error -> {
                snackbar.showSnackbar("$errorPrefix${(state as BackupUiState.Error).message}")
                viewModel.dismissResult()
            }
            is BackupUiState.PendingImport -> if (confirmImportImmediately) {
                viewModel.confirmImport((state as BackupUiState.PendingImport).availableCategories)
            }
            else -> Unit
        }
    }

    Scaffold(
        topBar = {
            if (showTopBar) {
                TopAppBar(
                    title = { Text(stringResource(R.string.backup_title)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.settings_back),
                            )
                        }
                    },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar, modifier = Modifier.padding(bottom = 80.dp)) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (showWarning) {
                Text(
                    text = stringResource(R.string.backup_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(8.dp))
            }

            if (state == BackupUiState.InProgress) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            } else {
                if (!importOnly) {
                    Button(
                        onClick = { showExportDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = state == BackupUiState.Idle,
                    ) {
                        Text(stringResource(R.string.backup_export_button))
                    }
                }

                OutlinedButton(
                    onClick = { importLauncher.launch(arrayOf("*/*")) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = state == BackupUiState.Idle,
                ) {
                    Text(stringResource(R.string.backup_import_button))
                }
            }
        }
    }

    if (showExportDialog) {
        CategoryPickerDialog(
            title = stringResource(R.string.backup_select_export_title),
            available = BackupCategory.ALL,
            initiallySelected = BackupCategory.ALL,
            onConfirm = { selected ->
                showExportDialog = false
                if (selected.isNotEmpty()) {
                    pendingExportCategories = selected
                    exportLauncher.launch(filename)
                }
            },
            onDismiss = { showExportDialog = false },
        )
    }

    if (!confirmImportImmediately) {
        (state as? BackupUiState.PendingImport)?.let { pending ->
            CategoryPickerDialog(
                title = stringResource(R.string.backup_select_import_title),
                available = pending.availableCategories,
                initiallySelected = pending.availableCategories,
                onConfirm = { selected -> viewModel.confirmImport(selected) },
                onDismiss = { viewModel.cancelImport() },
            )
        }
    }
}

@Composable
private fun CategoryPickerDialog(
    title: String,
    available: Set<BackupCategory>,
    initiallySelected: Set<BackupCategory>,
    onConfirm: (Set<BackupCategory>) -> Unit,
    onDismiss: () -> Unit,
) {
    var selected by remember { mutableStateOf(initiallySelected) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                BackupCategory.ALL.forEach { category ->
                    val enabled = category in available
                    val checked = enabled && category in selected
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = checked,
                            enabled = enabled,
                            onCheckedChange = { isChecked ->
                                selected = if (isChecked) selected + category else selected - category
                            },
                        )
                        Spacer(Modifier.padding(end = 8.dp))
                        Text(
                            text = stringResource(category.labelRes()),
                            color = if (enabled) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(selected.intersect(available)) },
                enabled = selected.intersect(available).isNotEmpty(),
            ) {
                Text(stringResource(R.string.backup_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.backup_cancel))
            }
        },
    )
}

private fun BackupCategory.labelRes(): Int = when (this) {
    BackupCategory.GENERAL_SETTINGS -> R.string.backup_category_general
    BackupCategory.DNS_HOSTS -> R.string.backup_category_dns_hosts
    BackupCategory.BYEDPI -> R.string.backup_category_byedpi
    BackupCategory.WARP -> R.string.backup_category_warp
    BackupCategory.URNETWORK -> R.string.backup_category_urnetwork
    BackupCategory.STRATEGY -> R.string.backup_category_strategy
    BackupCategory.SPLIT_TUNNEL -> R.string.backup_category_split_tunnel
}
