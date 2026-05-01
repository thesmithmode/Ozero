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
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.ozero.app.R
import ru.ozero.enginewarp.WarpConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WarpEngineSettingsScreen(
    onBack: () -> Unit,
    viewModel: WarpEngineSettingsViewModel = hiltViewModel(),
) {
    BackHandler(onBack = onBack)
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Scaffold(
        modifier = Modifier.testTag("warp_settings"),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.warp_settings_title)) },
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
            val cfg = state.currentConfig
            if (cfg == null) {
                EmptyConfigCard(
                    isRegistering = state.isRegistering,
                    errorMessage = state.errorMessage,
                    onGenerate = viewModel::onGenerate,
                )
            } else {
                ConfigCard(
                    config = cfg,
                    isRegistering = state.isRegistering,
                    errorMessage = state.errorMessage,
                    onRegenerate = viewModel::onGenerate,
                    onClear = viewModel::onClear,
                )
            }
        }
    }
}

@Composable
private fun EmptyConfigCard(
    isRegistering: Boolean,
    errorMessage: String?,
    onGenerate: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.warp_no_config),
                style = MaterialTheme.typography.titleMedium,
            )
            if (errorMessage != null) {
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Button(
                onClick = onGenerate,
                enabled = !isRegistering,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("warp_generate_button"),
            ) {
                if (isRegistering) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(end = 8.dp),
                            strokeWidth = 2.dp,
                        )
                        Text(stringResource(R.string.warp_registering))
                    }
                } else {
                    Text(stringResource(R.string.warp_generate))
                }
            }
        }
    }
}

@Composable
private fun ConfigCard(
    config: WarpConfig,
    isRegistering: Boolean,
    errorMessage: String?,
    onRegenerate: () -> Unit,
    onClear: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.warp_endpoint_label),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(text = config.peerEndpoint, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = stringResource(R.string.warp_license_label),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = config.accountLicense.take(LICENSE_PREVIEW_LEN) + "…",
                style = MaterialTheme.typography.bodyMedium,
            )
            if (errorMessage != null) {
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Button(
            onClick = onRegenerate,
            enabled = !isRegistering,
            modifier = Modifier
                .weight(1f)
                .testTag("warp_regenerate_button"),
        ) {
            Text(
                text = if (isRegistering) {
                    stringResource(R.string.warp_registering)
                } else {
                    stringResource(R.string.warp_regenerate)
                },
            )
        }
        OutlinedButton(
            onClick = onClear,
            enabled = !isRegistering,
            modifier = Modifier
                .weight(1f)
                .testTag("warp_clear_button"),
        ) {
            Text(stringResource(R.string.warp_clear))
        }
    }
}

private const val LICENSE_PREVIEW_LEN = 8
