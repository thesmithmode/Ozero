package ru.ozero.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import ru.ozero.coreorchestrator.OrchestratorState

@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onConnectClick: () -> Unit,
) {
    val state by viewModel.state.collectAsState()

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(24.dp),
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
                CircularProgressIndicator(
                    modifier =
                        Modifier
                            .size(48.dp)
                            .semantics { contentDescription = "Загрузка" },
                )
            }
            else -> {
                val isConnected = state is OrchestratorState.Connected
                Button(
                    onClick = onConnectClick,
                    modifier =
                        Modifier.semantics {
                            contentDescription = if (isConnected) "Отключить VPN" else "Подключить VPN"
                        },
                ) {
                    Text(if (isConnected) "Выключить" else "Включить")
                }
            }
        }
    }
}

@Composable
private fun StatusLabel(state: OrchestratorState) {
    val (label, engine) =
        when (state) {
            is OrchestratorState.Idle -> "Выключено" to null
            is OrchestratorState.Probing -> "Поиск маршрута..." to null
            is OrchestratorState.Connecting -> "Подключение..." to state.engineId.name
            is OrchestratorState.Connected -> "Подключено" to state.engineId.name
            is OrchestratorState.Switching -> "Переключение..." to null
            is OrchestratorState.Failed -> "Ошибка" to state.engineId.name
            is OrchestratorState.Disconnecting -> "Отключение..." to null
        }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
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
