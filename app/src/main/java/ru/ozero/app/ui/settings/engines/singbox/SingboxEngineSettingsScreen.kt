package ru.ozero.app.ui.settings.engines.singbox

import android.net.Uri
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.ozero.app.R
import ru.ozero.singboxroom.entity.ProxyProfile
import ru.ozero.singboxroom.entity.SubscriptionGroup

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SingboxEngineSettingsScreen(
    onBack: () -> Unit,
    onOpenAdvanced: () -> Unit = {},
    viewModel: SingboxEngineSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val filePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val text = runCatching {
            context.contentResolver.openInputStream(uri)?.use { it.bufferedReader().readText() }
        }.getOrNull() ?: return@rememberLauncherForActivityResult
        val fileName = uri.lastPathSegment
        viewModel.onImportFromFile(text, fileName)
    }

    if (state.showAddGroupDialog) {
        AddGroupDialog(
            name = state.addGroupName,
            url = state.addGroupUrl,
            error = state.addGroupError,
            onNameChanged = { viewModel.onAddGroupFieldChanged(name = it) },
            onUrlChanged = { viewModel.onAddGroupFieldChanged(url = it) },
            onConfirm = viewModel::onAddGroupConfirm,
            onDismiss = { viewModel.onAddGroupDialog(false) },
        )
    }

    if (state.showAddManualLinksDialog) {
        AddManualLinksDialog(
            input = state.manualLinksInput,
            groupName = state.manualLinksGroupName,
            error = state.manualLinksError,
            onInputChanged = { viewModel.onManualLinksFieldChanged(input = it) },
            onGroupNameChanged = { viewModel.onManualLinksFieldChanged(groupName = it) },
            onConfirm = viewModel::onConfirmManualLinks,
            onDismiss = { viewModel.onShowAddManualLinksDialog(false) },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_singbox_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    SingboxTopBarActions(
                        state = state,
                        viewModel = viewModel,
                        filePickerLauncher = filePickerLauncher,
                        onOpenAdvanced = onOpenAdvanced,
                    )
                },
            )
        },
    ) { padding ->
        SingboxSettingsContent(state = state, viewModel = viewModel, modifier = Modifier.padding(padding))
    }
}

@Composable
private fun SingboxSettingsContent(
    state: SingboxSettingsUiState,
    viewModel: SingboxEngineSettingsViewModel,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Spacer(Modifier.height(8.dp))

        if (state.groups.isNotEmpty()) {
            AutoSelectModeItem(
                isSelected = state.isAutoSelectMode,
                onClick = { viewModel.onSetAutoSelect(!state.isAutoSelectMode) },
            )
            Spacer(Modifier.height(4.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))
        }

        if (state.allProfiles.isNotEmpty()) {
            SingboxChainSection(
                allProfiles = state.allProfiles,
                chainProfileIds = state.chainProfileIds,
                selectedProfileId = state.selectedProfileId,
                onAdd = viewModel::onChainProfileAdd,
                onRemove = viewModel::onChainProfileRemove,
                onMove = viewModel::onChainProfileMove,
            )
            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))
        }

        state.groups.forEach { group ->
            SubscriptionGroupItem(
                group = group,
                isExpanded = state.expandedGroupId == group.id,
                profiles = state.groupProfiles[group.id] ?: emptyList(),
                selectedProfileId = state.selectedProfileId,
                isRefreshing = group.id in state.isRefreshing,
                isPinging = group.id in state.isPinging,
                refreshError = state.groupRefreshErrors[group.id],
                testingProfileIds = state.testingProfileIds,
                onToggle = { viewModel.onGroupExpand(group.id) },
                onRefresh = { viewModel.onRefresh(group.id) },
                onPing = { viewModel.onPing(group.id) },
                onDelete = { viewModel.onDeleteGroup(group) },
                onProfileSelect = { viewModel.onProfileSelect(it) },
            )
            Spacer(Modifier.height(4.dp))
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SingboxChainSection(
    allProfiles: List<ProxyProfile>,
    chainProfileIds: List<Long>,
    selectedProfileId: Long?,
    onAdd: (ProxyProfile) -> Unit,
    onRemove: (Long) -> Unit,
    onMove: (Long, Int) -> Unit,
) {
    var showChainAddDialog by remember { mutableStateOf(false) }
    val chain = if (chainProfileIds.isEmpty()) {
        emptyList()
    } else {
        val profilesById = allProfiles.associateBy { it.id }
        chainProfileIds
            .mapNotNull { profilesById[it] }
            .filterNot { it.id == selectedProfileId }
    }
    val canAdd = allProfiles.any { profile -> profile.id != selectedProfileId && profile.id !in chainProfileIds }
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.singbox_chain_title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(vertical = 4.dp),
        )
        Text(
            text = stringResource(R.string.singbox_chain_summary),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        if (chain.isEmpty()) {
            Text(
                text = stringResource(R.string.singbox_chain_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        } else {
            chain.forEachIndexed { index, profile ->
                SingboxChainStepRow(
                    profile = profile,
                    index = index,
                    isFirst = index == 0,
                    isLast = index == chain.lastIndex,
                    onRemove = { onRemove(profile.id) },
                    onMoveUp = { onMove(profile.id, -1) },
                    onMoveDown = { onMove(profile.id, 1) },
                )
            }
            Spacer(Modifier.height(8.dp))
        }
        TextButton(
            onClick = { showChainAddDialog = true },
            enabled = canAdd,
            modifier = Modifier.testTag("singbox_chain_add"),
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text(stringResource(R.string.singbox_chain_add))
        }
        if (showChainAddDialog) {
            val addable = allProfiles.filter { profile ->
                profile.id != selectedProfileId && profile.id !in chainProfileIds
            }
            AlertDialog(
                onDismissRequest = { showChainAddDialog = false },
                title = { Text(stringResource(R.string.singbox_chain_add)) },
                text = {
                    LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                        items(addable, key = { it.id }) { profile ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        showChainAddDialog = false
                                        onAdd(profile)
                                    }
                                    .padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(profile.name, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { showChainAddDialog = false }) {
                        Text(stringResource(R.string.singbox_cancel))
                    }
                },
            )
        }
    }
}

@Composable
private fun SingboxChainStepRow(
    profile: ProxyProfile,
    index: Int,
    isFirst: Boolean,
    isLast: Boolean,
    onRemove: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = profile.name,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = stringResource(R.string.singbox_chain_step, index + 1),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onMoveUp, enabled = !isFirst, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Default.KeyboardArrowUp, contentDescription = null)
        }
        IconButton(onClick = onMoveDown, enabled = !isLast, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Default.KeyboardArrowDown, contentDescription = null)
        }
        IconButton(onClick = onRemove, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.singbox_chain_remove))
        }
    }
}

@Composable
private fun AutoSelectModeItem(
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp)
            .testTag("singbox_auto_mode_item"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = isSelected, onClick = null)
        Spacer(Modifier.width(4.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.singbox_auto_mode_title),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = stringResource(R.string.singbox_auto_mode_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SubscriptionGroupItem(
    group: SubscriptionGroup,
    isExpanded: Boolean,
    profiles: List<ProxyProfile>,
    selectedProfileId: Long?,
    isRefreshing: Boolean,
    isPinging: Boolean,
    refreshError: String?,
    testingProfileIds: Set<Long>,
    onToggle: () -> Unit,
    onRefresh: () -> Unit,
    onPing: () -> Unit,
    onDelete: () -> Unit,
    onProfileSelect: (ProxyProfile) -> Unit,
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = group.name,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            if (isRefreshing || isPinging) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                TextButton(onClick = onPing, enabled = profiles.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.singbox_group_ping),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                if (group.subscriptionUrl.isNotEmpty()) {
                    IconButton(onClick = onRefresh, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.singbox_group_refresh))
                    }
                }
            }
            IconButton(
                onClick = onDelete,
                modifier = Modifier
                    .size(36.dp)
                    .testTag("singbox_delete_group_${group.id}"),
            ) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.singbox_group_delete))
            }
        }

        if (isExpanded) {
            if (refreshError != null) {
                Text(
                    text = stringResource(R.string.singbox_refresh_error, refreshError),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(start = 28.dp, bottom = 8.dp),
                )
            }
            if (profiles.isEmpty() && !isRefreshing && refreshError == null) {
                Text(
                    text = stringResource(R.string.singbox_no_profiles_hint),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(start = 28.dp, bottom = 8.dp),
                )
            } else {
                profiles.forEach { profile ->
                    ProfileItem(
                        profile = profile,
                        isSelected = profile.id == selectedProfileId,
                        isTesting = profile.id in testingProfileIds,
                        onSelect = { onProfileSelect(profile) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileItem(
    profile: ProxyProfile,
    isSelected: Boolean,
    isTesting: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(start = 28.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = isSelected,
            onClick = null,
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = profile.name,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
        )
        when {
            isTesting -> {
                Spacer(Modifier.width(4.dp))
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            }
            profile.latencyMs >= 0 -> {
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "${profile.latencyMs}ms",
                    style = MaterialTheme.typography.labelSmall,
                    color = when {
                        profile.latencyMs < 200 -> MaterialTheme.colorScheme.primary
                        profile.latencyMs < 500 -> MaterialTheme.colorScheme.secondary
                        else -> MaterialTheme.colorScheme.error
                    },
                )
            }
            profile.latencyMs == SingboxProbeService.LATENCY_FAILED -> {
                Spacer(Modifier.width(4.dp))
                Text(
                    text = stringResource(R.string.singbox_latency_failed),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun AddGroupDialog(
    name: String,
    url: String,
    error: String?,
    onNameChanged: (String) -> Unit,
    onUrlChanged: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.singbox_add_group_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = onNameChanged,
                    label = { Text(stringResource(R.string.singbox_group_name_hint)) },
                    placeholder = { Text(stringResource(R.string.singbox_group_name_auto_hint)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("singbox_add_group_name"),
                    singleLine = true,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = url,
                    onValueChange = onUrlChanged,
                    label = { Text(stringResource(R.string.singbox_group_url_hint)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("singbox_add_group_url"),
                    singleLine = true,
                    isError = error != null,
                    supportingText = if (error != null) {
                        { Text(stringResource(R.string.singbox_add_error_empty_fields)) }
                    } else {
                        null
                    },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                modifier = Modifier.testTag("singbox_add_group_confirm"),
            ) {
                Text(stringResource(R.string.singbox_add_group_button))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.singbox_cancel))
            }
        },
    )
}

@Composable
private fun SingboxTopBarActions(
    state: SingboxSettingsUiState,
    viewModel: SingboxEngineSettingsViewModel,
    filePickerLauncher: ManagedActivityResultLauncher<Array<String>, Uri?>,
    onOpenAdvanced: () -> Unit,
) {
    Box {
        IconButton(
            onClick = { viewModel.onShowAddMenu(true) },
            modifier = Modifier.testTag("singbox_add_button"),
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
        }
        DropdownMenu(
            expanded = state.showAddMenu,
            onDismissRequest = { viewModel.onShowAddMenu(false) },
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.singbox_add_menu_subscription)) },
                onClick = {
                    viewModel.onShowAddMenu(false)
                    viewModel.onAddGroupDialog(true)
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.singbox_add_menu_manual)) },
                onClick = { viewModel.onShowAddManualLinksDialog(true) },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.singbox_add_menu_file)) },
                onClick = {
                    viewModel.onShowAddMenu(false)
                    filePickerLauncher.launch(arrayOf("text/*", "application/octet-stream"))
                },
            )
        }
    }
    if (state.isPinging.isNotEmpty()) {
        IconButton(
            onClick = { viewModel.onCancel(ping = true) },
            modifier = Modifier.testTag("singbox_cancel_ping_button"),
        ) {
            Icon(Icons.Default.Close, contentDescription = null)
        }
    } else {
        TextButton(
            onClick = { viewModel.onPing() },
            enabled = state.groups.isNotEmpty(),
            modifier = Modifier.testTag("singbox_ping_all_button"),
        ) {
            Text(
                text = stringResource(R.string.singbox_ping_all_button),
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
    if (state.isRefreshing.isNotEmpty()) {
        IconButton(
            onClick = { viewModel.onCancel(refresh = true) },
            modifier = Modifier.testTag("singbox_cancel_refresh_button"),
        ) {
            Icon(Icons.Default.Close, contentDescription = null)
        }
    } else {
        IconButton(
            onClick = { viewModel.onRefresh() },
            enabled = state.groups.isNotEmpty(),
            modifier = Modifier.testTag("singbox_refresh_all_button"),
        ) {
            Icon(
                Icons.Default.Refresh,
                contentDescription = stringResource(R.string.singbox_refresh_all_button),
            )
        }
    }
    IconButton(
        onClick = onOpenAdvanced,
        modifier = Modifier.testTag("singbox_advanced_settings_button"),
    ) {
        Icon(Icons.Filled.Settings, contentDescription = null)
    }
}

@Composable
private fun AddManualLinksDialog(
    input: String,
    groupName: String,
    error: String?,
    onInputChanged: (String) -> Unit,
    onGroupNameChanged: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.singbox_add_menu_manual)) },
        text = {
            Column {
                OutlinedTextField(
                    value = groupName,
                    onValueChange = onGroupNameChanged,
                    label = { Text(stringResource(R.string.singbox_manual_links_name_hint)) },
                    placeholder = { Text(stringResource(R.string.singbox_group_name_auto_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = input,
                    onValueChange = onInputChanged,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("vless://, vmess://, trojan://, ss://") },
                    isError = error != null,
                    supportingText = if (error != null) {
                        {
                            Text(
                                when (error) {
                                    "empty" -> stringResource(R.string.singbox_manual_links_error_empty)
                                    else -> stringResource(R.string.singbox_manual_links_error_parse)
                                },
                            )
                        }
                    } else {
                        null
                    },
                    singleLine = false,
                    minLines = 3,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.singbox_save_button))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.singbox_cancel))
            }
        },
    )
}
