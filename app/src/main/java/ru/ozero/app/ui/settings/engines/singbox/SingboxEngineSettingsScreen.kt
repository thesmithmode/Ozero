package ru.ozero.app.ui.settings.engines.singbox

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    viewModel: SingboxEngineSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    if (state.showAddGroupDialog) {
        AddGroupDialog(
            name = state.addGroupName,
            url = state.addGroupUrl,
            error = state.addGroupError,
            onNameChanged = viewModel::onAddGroupNameChanged,
            onUrlChanged = viewModel::onAddGroupUrlChanged,
            onConfirm = viewModel::onAddGroupConfirm,
            onDismiss = viewModel::onAddGroupDialogDismiss,
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
                    IconButton(
                        onClick = viewModel::onRefreshAll,
                        enabled = state.groups.isNotEmpty() && state.isRefreshing.isEmpty(),
                        modifier = Modifier.testTag("singbox_refresh_all_button"),
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.singbox_refresh_all_button),
                        )
                    }
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

        state.groups.forEach { group ->
            SubscriptionGroupItem(
                group = group,
                isExpanded = state.expandedGroupId == group.id,
                profiles = state.groupProfiles[group.id] ?: emptyList(),
                selectedProfileId = state.selectedProfileId,
                isRefreshing = group.id in state.isRefreshing,
                isPinging = group.id in state.isPinging,
                refreshError = state.groupRefreshErrors[group.id],
                onToggle = { viewModel.onGroupExpand(group.id) },
                onRefresh = { viewModel.onRefreshGroup(group.id) },
                onPing = { viewModel.onPingGroup(group.id) },
                onDelete = { viewModel.onDeleteGroup(group) },
                onProfileSelect = { viewModel.onProfileSelect(it) },
            )
            Spacer(Modifier.height(4.dp))
        }

        Spacer(Modifier.height(8.dp))

        TextButton(
            onClick = viewModel::onAddGroupDialogOpen,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("singbox_add_group_button"),
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.width(4.dp))
            Text(stringResource(R.string.singbox_add_group_button))
        }

        Spacer(Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(Modifier.height(12.dp))

        Text(
            text = stringResource(R.string.singbox_custom_link_section),
            style = MaterialTheme.typography.labelMedium,
        )
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = state.customLinkInput,
            onValueChange = viewModel::onCustomLinkChanged,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("singbox_link_input"),
            placeholder = { Text("vless://...") },
            isError = state.customLinkError != null,
            supportingText = state.customLinkError?.let { err ->
                {
                    Text(
                        when (err) {
                            is CustomLinkError.Empty -> stringResource(R.string.singbox_link_error_empty)
                            is CustomLinkError.ParseFailed ->
                                stringResource(R.string.singbox_link_error_parse, err.cause)
                            is CustomLinkError.SaveFailed ->
                                stringResource(R.string.singbox_link_error_save, err.cause)
                        },
                    )
                }
            },
            singleLine = false,
            minLines = 2,
        )
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = viewModel::onSaveCustomLink,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("singbox_save_button"),
        ) {
            Text(stringResource(R.string.singbox_save_button))
        }
        if (state.customLinkSaved) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.singbox_saved_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.testTag("singbox_saved_hint"),
            )
        }

        if (state.groups.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.singbox_auto_select_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = viewModel::onAutoSelectBest,
                enabled = !state.isAutoSelecting && state.isRefreshing.isEmpty() && state.isPinging.isEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("singbox_auto_select_button"),
            ) {
                if (state.isAutoSelecting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(stringResource(R.string.singbox_auto_select_button))
            }
        }

        Spacer(Modifier.height(24.dp))
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
                IconButton(onClick = onPing, modifier = Modifier.size(36.dp), enabled = profiles.isNotEmpty()) {
                    Icon(Icons.Default.Star, contentDescription = stringResource(R.string.singbox_group_ping))
                }
                IconButton(onClick = onRefresh, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.singbox_group_refresh))
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
            if (profiles.isEmpty() && !isRefreshing) {
                if (refreshError != null) {
                    Text(
                        text = stringResource(R.string.singbox_refresh_error, refreshError),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(start = 28.dp, bottom = 8.dp),
                    )
                } else {
                    Text(
                        text = stringResource(R.string.singbox_no_profiles_hint),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 28.dp, bottom = 8.dp),
                    )
                }
            } else {
                profiles.forEach { profile ->
                    ProfileItem(
                        profile = profile,
                        isSelected = profile.id == selectedProfileId,
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
        if (profile.latencyMs >= 0) {
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
