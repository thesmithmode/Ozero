package ru.ozero.app.ui.diag

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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

@Composable
fun DiagnosticsScreen(
    onBack: () -> Unit,
    viewModel: DiagnosticsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    DiagnosticsScreenContent(
        state = state,
        onBack = onBack,
        onRun = viewModel::onRun,
        onStop = viewModel::onStop,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreenContent(
    state: DiagnosticsUiState,
    onBack: () -> Unit,
    onRun: () -> Unit,
    onStop: () -> Unit,
) {
    Scaffold(
        modifier = Modifier.testTag(DiagnosticsTestTags.SCREEN),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.diag_title)) },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag(DiagnosticsTestTags.BACK),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.diag_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        when (state) {
            DiagnosticsUiState.NotConnected -> NotConnectedBody(padding)
            DiagnosticsUiState.Idle -> IdleBody(padding, onRun)
            is DiagnosticsUiState.Running -> RunningBody(padding, state, onStop)
            is DiagnosticsUiState.Done -> DoneBody(padding, state, onRun)
        }
    }
}

@Composable
private fun NotConnectedBody(padding: PaddingValues) {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp)
                .testTag(DiagnosticsTestTags.NOT_CONNECTED),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.diag_not_connected),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun IdleBody(
    padding: PaddingValues,
    onRun: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp)
                .testTag(DiagnosticsTestTags.IDLE),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.diag_idle_hint),
            style = MaterialTheme.typography.bodyMedium,
        )
        Button(
            onClick = onRun,
            modifier =
                Modifier
                    .padding(top = 24.dp)
                    .testTag(DiagnosticsTestTags.RUN_BUTTON),
        ) {
            Text(stringResource(R.string.diag_run))
        }
    }
}

@Composable
private fun RunningBody(
    padding: PaddingValues,
    state: DiagnosticsUiState.Running,
    onStop: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.diag_running, state.completed, state.total),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.testTag(DiagnosticsTestTags.PROGRESS_LABEL),
        )
        val progress = if (state.total == 0) 0f else state.completed.toFloat() / state.total
        LinearProgressIndicator(
            progress = { progress },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp)
                    .testTag(DiagnosticsTestTags.PROGRESS),
        )
        OutlinedButton(
            onClick = onStop,
            modifier = Modifier.testTag(DiagnosticsTestTags.STOP_BUTTON),
        ) {
            Text(stringResource(R.string.diag_stop))
        }
    }
}

@Composable
private fun DoneBody(
    padding: PaddingValues,
    state: DiagnosticsUiState.Done,
    onRun: () -> Unit,
) {
    val successCount = state.results.count { it is DiagResult.Success }
    val failureCount = state.results.size - successCount
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(padding),
    ) {
        Text(
            text =
                stringResource(
                    R.string.diag_summary,
                    successCount,
                    failureCount,
                    state.results.size,
                ),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(16.dp),
        )
        HorizontalDivider()
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .testTag(DiagnosticsTestTags.RESULTS_LIST),
            contentPadding = PaddingValues(vertical = 8.dp),
        ) {
            items(state.results, key = { it.url }) { result ->
                ResultRow(result)
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            }
        }
        Button(
            onClick = onRun,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .testTag(DiagnosticsTestTags.RUN_BUTTON),
        ) {
            Text(stringResource(R.string.diag_run))
        }
    }
}

@Composable
private fun ResultRow(result: DiagResult) {
    val text =
        when (result) {
            is DiagResult.Success ->
                stringResource(R.string.diag_url_ok, result.url, result.latencyMs)
            is DiagResult.Failure ->
                stringResource(R.string.diag_url_failed, result.url, result.reason)
        }
    val color =
        when (result) {
            is DiagResult.Success -> MaterialTheme.colorScheme.onSurface
            is DiagResult.Failure -> MaterialTheme.colorScheme.error
        }
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = color,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .testTag(DiagnosticsTestTags.RESULT_PREFIX + result.url),
    )
}
