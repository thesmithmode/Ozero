package ru.ozero.app.ui.servers

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.ozero.app.R
import ru.ozero.corestorage.entity.ServerEntity

@Composable
fun ServersScreen(
    onBack: () -> Unit,
    viewModel: ServersViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    BackHandler(onBack = onBack)
    ServersScreenContent(
        state = state,
        onBack = onBack,
        onEntrySelect = viewModel::onEntrySelect,
        onExitSelect = viewModel::onExitSelect,
        onSavePair = viewModel::onSavePair,
        onClearPair = viewModel::onClearPair,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServersScreenContent(
    state: ServersUiState,
    onBack: () -> Unit,
    onEntrySelect: (String) -> Unit,
    onExitSelect: (String) -> Unit,
    onSavePair: () -> Unit,
    onClearPair: () -> Unit,
) {
    Scaffold(
        modifier = Modifier.testTag(ServersTestTags.SCREEN),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.servers_title)) },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag(ServersTestTags.BACK),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.servers_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        when (state) {
            ServersUiState.Loading -> LoadingBody(padding)
            ServersUiState.Empty -> EmptyBody(padding)
            is ServersUiState.Content ->
                ContentBody(
                    padding = padding,
                    state = state,
                    onEntrySelect = onEntrySelect,
                    onExitSelect = onExitSelect,
                    onSavePair = onSavePair,
                    onClearPair = onClearPair,
                )
        }
    }
}

@Composable
private fun LoadingBody(padding: PaddingValues) {
    Box(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(modifier = Modifier.testTag(ServersTestTags.LOADING))
    }
}

@Composable
private fun EmptyBody(padding: PaddingValues) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(24.dp)
            .testTag(ServersTestTags.EMPTY),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.servers_empty),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun ContentBody(
    padding: PaddingValues,
    state: ServersUiState.Content,
    onEntrySelect: (String) -> Unit,
    onExitSelect: (String) -> Unit,
    onSavePair: () -> Unit,
    onClearPair: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ServerDropdown(
            label = stringResource(R.string.servers_entry_label),
            servers = state.servers,
            selectedId = state.entryId,
            onSelect = onEntrySelect,
            tag = ServersTestTags.ENTRY_DROPDOWN,
            optionTagPrefix = ServersTestTags.ENTRY_OPTION_PREFIX,
        )
        ServerDropdown(
            label = stringResource(R.string.servers_exit_label),
            servers = state.servers,
            selectedId = state.exitId,
            onSelect = onExitSelect,
            tag = ServersTestTags.EXIT_DROPDOWN,
            optionTagPrefix = ServersTestTags.EXIT_OPTION_PREFIX,
        )
        PreviewCard(state)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onSavePair,
                enabled = state.canSave,
                modifier = Modifier.testTag(ServersTestTags.SAVE),
            ) {
                Text(stringResource(R.string.servers_save))
            }
            OutlinedButton(
                onClick = onClearPair,
                enabled = state.canSave,
                modifier = Modifier.testTag(ServersTestTags.CLEAR),
            ) {
                Text(stringResource(R.string.servers_clear))
            }
        }
    }
}

@Composable
private fun ServerDropdown(
    label: String,
    servers: List<ServerEntity>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    tag: String,
    optionTagPrefix: String,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = servers.firstOrNull { it.id == selectedId }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .testTag(tag),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = label, style = MaterialTheme.typography.labelMedium)
            Text(
                text = selected?.label() ?: "-",
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            servers.forEach { server ->
                DropdownMenuItem(
                    text = {
                        Text(server.label())
                    },
                    onClick = {
                        onSelect(server.id)
                        expanded = false
                    },
                    modifier = Modifier.testTag(optionTagPrefix + server.id),
                )
            }
        }
    }
}

private fun ServerEntity.label(): String = "$country - $protocol ($role)"

@Composable
private fun PreviewCard(state: ServersUiState.Content) {
    Card(modifier = Modifier.fillMaxWidth().testTag(ServersTestTags.PREVIEW)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = stringResource(R.string.servers_preview_title),
                style = MaterialTheme.typography.labelMedium,
            )
            val entry = state.entry
            val exit = state.exit
            val text = if (entry != null && exit != null && state.canSave) {
                stringResource(
                    R.string.servers_preview_format,
                    entry.country,
                    entry.protocol,
                    exit.country,
                    exit.protocol,
                )
            } else {
                stringResource(R.string.servers_preview_unset)
            }
            Text(text = text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
