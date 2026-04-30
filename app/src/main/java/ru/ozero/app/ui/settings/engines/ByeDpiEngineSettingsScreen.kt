package ru.ozero.app.ui.settings.engines

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.ozero.enginebyedpi.strategy.StrategyScore

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ByeDpiEngineSettingsScreen(
    onBack: () -> Unit,
    viewModel: ByeDpiEngineSettingsViewModel = hiltViewModel(),
) {
    BackHandler(onBack = onBack)
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val autoTestState by viewModel.autoTestState.collectAsStateWithLifecycle()
    Scaffold(
        modifier = Modifier.testTag("byedpi_settings"),
        topBar = {
            TopAppBar(
                title = { Text("ByeDPI") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        when (val s = state) {
            ByeDpiSettingsUiState.Loading -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator()
                }
            }
            is ByeDpiSettingsUiState.Content -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = "Аргументы запуска libbyedpi. Используются для обхода ТСПУ DPI. " +
                            "Подробности: github.com/hufrea/byedpi",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    OutlinedTextField(
                        value = s.args,
                        onValueChange = viewModel::onArgsChange,
                        label = { Text("ByeDPI args") },
                        modifier = Modifier.fillMaxWidth().testTag("byedpi_args_field"),
                        minLines = 2,
                    )
                    Text(
                        text = if (s.usingDefault) {
                            "Используется default: ${s.defaultArgs}"
                        } else {
                            "Сохранённый override активен"
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = viewModel::onSave,
                            enabled = s.dirty,
                            modifier = Modifier.weight(1f).testTag("byedpi_save_btn"),
                        ) {
                            Text("Сохранить")
                        }
                        OutlinedButton(
                            onClick = viewModel::onResetToDefault,
                            enabled = !s.usingDefault,
                            modifier = Modifier.weight(1f).testTag("byedpi_reset_btn"),
                        ) {
                            Text("Сброс")
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    Text(
                        text = "Авто-подбор стратегии",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = "Тестирует 75 стратегий ByeDPI на нескольких сайтах через локальный SOCKS5. " +
                            "Лучшая по % успеха применяется. Перед запуском отключите VPN.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    AutoTestSection(
                        state = autoTestState,
                        onStart = viewModel::onStartAutoTest,
                        onCancel = viewModel::onCancelAutoTest,
                        onApply = viewModel::onApplyAutoTestStrategy,
                    )
                }
            }
        }
    }
}

@Composable
private fun AutoTestSection(
    state: AutoTestUiState,
    onStart: () -> Unit,
    onCancel: () -> Unit,
    onApply: (StrategyScore) -> Unit,
) {
    when (state) {
        AutoTestUiState.Idle -> {
            Button(
                onClick = onStart,
                modifier = Modifier.fillMaxWidth().testTag("byedpi_autotest_start_btn"),
            ) {
                Text("Запустить авто-тест (~5-10 мин)")
            }
        }
        is AutoTestUiState.Running -> {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val progress = if (state.total > 0) state.current.toFloat() / state.total.toFloat() else 0f
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().testTag("byedpi_autotest_progress"),
                )
                Text(
                    text = "Тестирую ${state.current}/${state.total}",
                    style = MaterialTheme.typography.bodySmall,
                )
                if (!state.lastCommand.isNullOrBlank()) {
                    Text(
                        text = state.lastCommand,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                }
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.fillMaxWidth().testTag("byedpi_autotest_cancel_btn"),
                ) {
                    Text("Отмена")
                }
            }
        }
        is AutoTestUiState.Done -> {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Готово. Top-${minOf(3, state.ranked.size)} стратегий:",
                    style = MaterialTheme.typography.bodyMedium,
                )
                state.ranked.take(3).forEach { score ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        ),
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            val percent = (score.successRate * 100).toInt()
                            val summary = "$percent% (${score.successCount}/${score.totalProbes}) · " +
                                "${score.avgDurationMs}ms"
                            Text(
                                text = summary,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                text = score.strategy.command,
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Button(
                                onClick = { onApply(score) },
                                modifier = Modifier.testTag("byedpi_autotest_apply_btn"),
                            ) {
                                Text("Применить")
                            }
                        }
                    }
                }
                OutlinedButton(
                    onClick = onStart,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Перезапустить тест")
                }
            }
        }
        is AutoTestUiState.Failed -> {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Ошибка: ${state.reason}",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(
                    onClick = onStart,
                    modifier = Modifier.fillMaxWidth().testTag("byedpi_autotest_retry_btn"),
                ) {
                    Text("Повторить")
                }
            }
        }
    }
}
