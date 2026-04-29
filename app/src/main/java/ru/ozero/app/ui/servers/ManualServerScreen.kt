package ru.ozero.app.ui.servers

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualServerScreen(
    onBack: () -> Unit,
    viewModel: ManualServerViewModel = hiltViewModel(),
) {
    BackHandler(onBack = onBack)
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Scaffold(
        modifier = Modifier.testTag("manual_server"),
        topBar = {
            TopAppBar(
                title = { Text("Добавить сервер") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Вставьте VLESS / Trojan / Hysteria2 / Shadowsocks URI. Сервер будет добавлен в БД и " +
                    "подхвачен engine кандидатами при следующем подключении.",
                style = MaterialTheme.typography.bodyMedium,
            )
            val currentUri = when (val s = state) {
                is ManualServerUiState.Idle -> s.uri
                is ManualServerUiState.Importing -> s.uri
                is ManualServerUiState.Error -> s.uri
                is ManualServerUiState.Success -> ""
            }
            OutlinedTextField(
                value = currentUri,
                onValueChange = viewModel::onUriChange,
                label = { Text("Server URI") },
                modifier = Modifier.fillMaxWidth().testTag("manual_uri_field"),
                singleLine = false,
                minLines = 2,
                enabled = state !is ManualServerUiState.Importing,
            )
            when (val s = state) {
                is ManualServerUiState.Importing -> {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                is ManualServerUiState.Error -> {
                    Text(
                        text = "Ошибка: ${s.reason}",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                is ManualServerUiState.Success -> {
                    Text(
                        text = "OK: добавлен сервер ${s.protocol}",
                        color = Color(0xFF2E7D32),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                is ManualServerUiState.Idle -> Unit
            }
            Button(
                onClick = viewModel::onAdd,
                enabled = state !is ManualServerUiState.Importing &&
                    (state is ManualServerUiState.Idle || state is ManualServerUiState.Error),
                modifier = Modifier.fillMaxWidth().testTag("manual_add_btn"),
            ) {
                Text("Добавить")
            }
            if (state is ManualServerUiState.Success || state is ManualServerUiState.Error) {
                OutlinedButton(
                    onClick = viewModel::onDismissResult,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Очистить")
                }
            }
        }
    }
}
