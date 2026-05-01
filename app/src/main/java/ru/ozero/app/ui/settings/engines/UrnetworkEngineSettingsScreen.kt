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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.ozero.app.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UrnetworkEngineSettingsScreen(
    onBack: () -> Unit,
    viewModel: UrnetworkEngineSettingsViewModel = hiltViewModel(),
) {
    BackHandler(onBack = onBack)
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Scaffold(
        modifier = Modifier.testTag("urnetwork_settings"),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.urnetwork_settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        when (val s = state) {
            UrnetworkSettingsUiState.Loading -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator()
                }
            }
            is UrnetworkSettingsUiState.Content -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (!s.consentGranted) {
                        ConsentBlock(
                            onGrant = viewModel::onGrantConsent,
                        )
                    } else {
                        WalletBlock(
                            content = s,
                            onWalletChange = viewModel::onWalletChange,
                            onSave = { viewModel.onSaveWallet(s.editedWallet) },
                            onReset = viewModel::onResetWallet,
                            onRevoke = viewModel::onRevokeConsent,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ConsentBlock(onGrant: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.urnetwork_consent_warning_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.urnetwork_consent_warning_body),
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(
                onClick = onGrant,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("urnetwork_consent_button"),
            ) {
                Text(stringResource(R.string.urnetwork_consent_accept))
            }
        }
    }
}

@Composable
private fun WalletBlock(
    content: UrnetworkSettingsUiState.Content,
    onWalletChange: (String) -> Unit,
    onSave: () -> Unit,
    onReset: () -> Unit,
    onRevoke: () -> Unit,
) {
    Text(
        text = stringResource(R.string.urnetwork_wallet_label),
        style = MaterialTheme.typography.titleSmall,
    )
    if (content.isUsingPreset) {
        Text(
            text = stringResource(R.string.urnetwork_wallet_using_preset),
            style = MaterialTheme.typography.bodySmall,
        )
    }
    OutlinedTextField(
        value = content.editedWallet,
        onValueChange = onWalletChange,
        label = { Text(stringResource(R.string.urnetwork_wallet_label)) },
        isError = content.errorMessage != null,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("urnetwork_wallet_field"),
        singleLine = true,
    )
    if (content.errorMessage != null) {
        Text(
            text = stringResource(R.string.urnetwork_wallet_invalid),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Button(
            onClick = onSave,
            modifier = Modifier.weight(1f).testTag("urnetwork_save_button"),
        ) {
            Text(stringResource(R.string.urnetwork_wallet_save))
        }
        if (!content.isUsingPreset) {
            OutlinedButton(
                onClick = onReset,
                modifier = Modifier.weight(1f).testTag("urnetwork_reset_button"),
            ) {
                Text(stringResource(R.string.urnetwork_wallet_reset))
            }
        }
    }
    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
    OutlinedButton(
        onClick = onRevoke,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("urnetwork_revoke_button"),
    ) {
        Text(stringResource(R.string.urnetwork_consent_revoke))
    }
}
