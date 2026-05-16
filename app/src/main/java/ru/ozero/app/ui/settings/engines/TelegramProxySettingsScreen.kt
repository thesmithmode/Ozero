package ru.ozero.app.ui.settings.engines

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.ozero.app.R
import ru.ozero.enginetelegram.TelegramProxyState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelegramProxySettingsScreen(
    onBack: () -> Unit,
    viewModel: TelegramProxySettingsViewModel = hiltViewModel(),
) {
    BackHandler(onBack = onBack)
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.telegram_proxy_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        when (val s = state) {
            TelegramProxyUiState.Loading -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) { CircularProgressIndicator() }
            }
            is TelegramProxyUiState.Content -> {
                ContentBody(
                    s = s,
                    modifier = Modifier.padding(padding),
                    onEnabledToggle = viewModel::onEnabledToggle,
                    onPortChange = viewModel::onPortChange,
                    onDomainChange = viewModel::onDomainChange,
                    onSavePort = viewModel::onSavePort,
                    onSaveDomain = viewModel::onSaveDomain,
                    onRegenerateSecret = viewModel::onRegenerateSecret,
                    onDismissGenerateError = viewModel::onDismissGenerateError,
                )
            }
        }
    }
}

@Composable
private fun ContentBody(
    s: TelegramProxyUiState.Content,
    modifier: Modifier = Modifier,
    onEnabledToggle: (Boolean) -> Unit,
    onPortChange: (String) -> Unit,
    onDomainChange: (String) -> Unit,
    onSavePort: () -> Unit,
    onSaveDomain: () -> Unit,
    onRegenerateSecret: () -> Unit,
    onDismissGenerateError: () -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.telegram_proxy_description),
            style = MaterialTheme.typography.bodyMedium,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.fillMaxWidth(0.78f)) {
                Text(stringResource(R.string.telegram_proxy_enable), style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = when (s.proxyState) {
                        TelegramProxyState.Idle -> stringResource(R.string.telegram_proxy_state_idle)
                        TelegramProxyState.Starting -> stringResource(R.string.telegram_proxy_state_starting)
                        is TelegramProxyState.Running -> stringResource(R.string.telegram_proxy_state_running_fmt, s.proxyState.port)
                        is TelegramProxyState.Error -> stringResource(R.string.telegram_proxy_state_error_fmt, s.proxyState.message)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = when (s.proxyState) {
                        is TelegramProxyState.Error -> MaterialTheme.colorScheme.error
                        is TelegramProxyState.Running -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    },
                )
            }
            Switch(checked = s.enabled, onCheckedChange = onEnabledToggle)
        }

        HorizontalDivider()

        OutlinedTextField(
            value = s.port,
            onValueChange = onPortChange,
            label = { Text(stringResource(R.string.telegram_proxy_port_label)) },
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = {
                OutlinedButton(onClick = onSavePort) { Text(stringResource(R.string.telegram_proxy_save)) }
            },
        )

        OutlinedTextField(
            value = s.domain,
            onValueChange = onDomainChange,
            label = { Text(stringResource(R.string.telegram_proxy_domain_label)) },
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = {
                OutlinedButton(onClick = onSaveDomain) { Text(stringResource(R.string.telegram_proxy_save)) }
            },
        )

        HorizontalDivider()

        if (s.secret.isNotBlank()) {
            Text(
                text = stringResource(R.string.telegram_proxy_secret_value_fmt, s.secret),
                style = MaterialTheme.typography.bodySmall,
            )
        } else {
            Text(
                text = stringResource(R.string.telegram_proxy_secret_missing),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = {
                    onDismissGenerateError()
                    onRegenerateSecret()
                },
                enabled = !s.generatingSecret,
                modifier = Modifier.weight(1f),
            ) {
                if (s.generatingSecret) {
                    CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
                }
                Text(if (s.secret.isBlank()) stringResource(R.string.telegram_proxy_generate_secret) else stringResource(R.string.telegram_proxy_rotate_secret))
            }
        }

        if (s.generateError) {
            Text(
                text = stringResource(R.string.telegram_proxy_generate_error),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        val link = s.tgLink
        if (link != null) {
            TgLinkSection(link = link)
        }
    }
}

@Composable
private fun TgLinkSection(link: String) {
    val context = LocalContext.current
    HorizontalDivider()
    Text(stringResource(R.string.telegram_proxy_link_label), style = MaterialTheme.typography.bodyMedium)
    Text(
        text = link,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.primary,
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedButton(
            onClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("tg_proxy", link))
            },
            modifier = Modifier.weight(1f),
        ) { Text(stringResource(R.string.telegram_proxy_copy)) }
        Button(
            onClick = {
                runCatching {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(link))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
            },
            modifier = Modifier.weight(1f),
        ) { Text(stringResource(R.string.telegram_proxy_open_in_tg)) }
    }
}
