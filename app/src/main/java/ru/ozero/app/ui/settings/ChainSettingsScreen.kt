package ru.ozero.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import ru.ozero.app.R
import ru.ozero.enginescore.EngineId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChainSettingsScreen(
    onBack: () -> Unit,
    viewModel: ChainSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.chain_screen_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.settings_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        ChainSettingsContent(
            padding = padding,
            state = state,
            onAddStep = viewModel::addStep,
            onRemoveStep = viewModel::removeStep,
            onClear = viewModel::clearChain,
        )
    }
}

@Composable
private fun ChainSettingsContent(
    padding: PaddingValues,
    state: ChainUiState,
    onAddStep: (EngineId) -> Unit,
    onRemoveStep: (Int) -> Unit,
    onClear: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (state.steps.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.chain_empty_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            }
        }

        itemsIndexed(state.steps) { index, step ->
            ChainStepCard(
                index = index,
                engineId = step.engineId,
                isHead = index == 0,
                isTail = index == state.steps.lastIndex,
                onRemove = { onRemoveStep(index) },
            )
        }

        item {
            AddEngineButton(
                availableEngines = state.availableEngines,
                onSelect = onAddStep,
            )
        }

        if (state.steps.isNotEmpty()) {
            item {
                TextButton(onClick = onClear, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.chain_remove_step))
                }
            }
        }
    }
}

@Composable
private fun ChainStepCard(
    index: Int,
    engineId: EngineId,
    isHead: Boolean,
    isTail: Boolean,
    onRemove: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${index + 1}. ${engineId.displayName}",
                    style = MaterialTheme.typography.bodyLarge,
                )
                val role = when {
                    isHead && isTail -> "standalone"
                    isHead -> "HEAD"
                    isTail -> "TAIL"
                    else -> "MID"
                }
                Text(
                    text = role,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                )
            }
            IconButton(onClick = onRemove) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = stringResource(R.string.chain_remove_step),
                )
            }
        }
    }
}

@Composable
private fun AddEngineButton(
    availableEngines: List<ChainableEngine>,
    onSelect: (EngineId) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    TextButton(
        onClick = { expanded = true },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(imageVector = Icons.Default.Add, contentDescription = null)
        Text(
            text = stringResource(R.string.chain_add_step),
            modifier = Modifier.padding(start = 4.dp),
        )
    }

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false },
    ) {
        availableEngines.forEach { engine ->
            DropdownMenuItem(
                text = { Text(engine.displayName) },
                onClick = {
                    onSelect(engine.id)
                    expanded = false
                },
            )
        }
    }
}
