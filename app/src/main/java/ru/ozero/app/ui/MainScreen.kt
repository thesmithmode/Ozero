package ru.ozero.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import ru.ozero.app.R
import ru.ozero.coreorchestrator.OrchestratorState

@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onConnectClick: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDiagnostics: () -> Unit,
) {
    val state by viewModel.state.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp),
        ) {
            IconButton(
                onClick = onOpenDiagnostics,
                modifier = Modifier.testTag(MainScreenTestTags.OPEN_DIAGNOSTICS),
            ) {
                Icon(
                    imageVector = Icons.Filled.Build,
                    contentDescription = stringResource(R.string.tab_diagnostics),
                )
            }
            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier.testTag(MainScreenTestTags.OPEN_SETTINGS),
            ) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = stringResource(R.string.tab_settings),
                )
            }
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            AnimatedContent(targetState = state, label = "status") { s ->
                StatusLabel(s)
            }

            Spacer(modifier = Modifier.height(32.dp))

            when (state) {
                is OrchestratorState.Probing,
                is OrchestratorState.Connecting,
                is OrchestratorState.Disconnecting,
                is OrchestratorState.Switching,
                -> {
                    val loadingDesc = stringResource(R.string.a11y_loading)
                    CircularProgressIndicator(
                        modifier = Modifier.size(48.dp).semantics { contentDescription = loadingDesc },
                    )
                }
                else -> {
                    val isConnected = state is OrchestratorState.Connected
                    val buttonDesc = stringResource(
                        if (isConnected) R.string.a11y_disconnect_button else R.string.a11y_connect_button,
                    )
                    Button(
                        onClick = onConnectClick,
                        modifier = Modifier.semantics { contentDescription = buttonDesc },
                    ) {
                        Text(stringResource(if (isConnected) R.string.main_disconnect else R.string.main_connect))
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusLabel(state: OrchestratorState) {
    val labelRes = when (state) {
        is OrchestratorState.Idle -> R.string.main_status_disconnected
        is OrchestratorState.Probing -> R.string.main_status_probing
        is OrchestratorState.Connecting -> R.string.main_status_connecting
        is OrchestratorState.Connected -> R.string.main_status_connected
        is OrchestratorState.Switching -> R.string.main_status_switching
        is OrchestratorState.Failed -> R.string.main_status_failed
        is OrchestratorState.Disconnecting -> R.string.main_status_disconnecting
    }
    val engine = when (state) {
        is OrchestratorState.Connecting -> state.engineId.name
        is OrchestratorState.Connected -> state.engineId.name
        is OrchestratorState.Failed -> state.engineId.name
        else -> null
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = stringResource(labelRes),
            style = MaterialTheme.typography.headlineMedium,
        )
        if (engine != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = engine,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
        }
    }
}
