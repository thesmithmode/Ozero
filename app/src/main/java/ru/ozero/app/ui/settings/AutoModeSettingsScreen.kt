package ru.ozero.app.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.ozero.app.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutoModeSettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    BackHandler(onBack = onBack)
    Scaffold(
        modifier = Modifier.testTag(SettingsTestTags.AUTO_MODE_SCREEN),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.auto_mode_screen_title)) },
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
        when (val s = state) {
            SettingsUiState.Loading -> Unit
            is SettingsUiState.Content -> AutoModeBody(
                padding = padding,
                priority = s.model.engineAutoPriority,
                manualEngineActive = s.model.manualEngine != null,
                onMove = { engine, delta ->
                    viewModel.onMoveAutoPriority(s.model.engineAutoPriority, engine, delta)
                },
            )
        }
    }
}

@Composable
private fun AutoModeBody(
    padding: PaddingValues,
    priority: List<ru.ozero.enginescore.EngineId>,
    manualEngineActive: Boolean,
    onMove: (ru.ozero.enginescore.EngineId, Int) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(vertical = 8.dp),
    ) {
        if (manualEngineActive) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .testTag(SettingsTestTags.AUTO_MODE_INACTIVE_WARNING),
                ) {
                    Text(
                        text = stringResource(R.string.auto_mode_inactive_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
        }
        item {
            AutoPriorityContent(
                priority = priority,
                onMove = onMove,
                showHeader = false,
            )
        }
    }
}
