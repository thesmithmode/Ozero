package ru.ozero.app.ui.splittunnel

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import ru.ozero.commonvpn.split.SplitTunnelMode

@Composable
fun SplitTunnelScreen(
    onBack: () -> Unit,
    viewModel: SplitTunnelViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    BackHandler(onBack = onBack)
    SplitTunnelScreenContent(
        state = state,
        onBack = onBack,
        onModeChange = viewModel::onModeChange,
        onToggleApp = viewModel::onToggleApp,
        onQuery = viewModel::onQuery,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SplitTunnelScreenContent(
    state: SplitTunnelUiState,
    onBack: () -> Unit,
    onModeChange: (SplitTunnelMode) -> Unit,
    onToggleApp: (String, Boolean) -> Unit,
    onQuery: (String) -> Unit,
) {
    Scaffold(
        modifier = Modifier.testTag(SplitTunnelTestTags.SCREEN),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.split_tunnel_title)) },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag(SplitTunnelTestTags.BACK),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.split_tunnel_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        when (state) {
            SplitTunnelUiState.Loading -> LoadingBody(padding)
            is SplitTunnelUiState.Content ->
                ContentBody(
                    padding = padding,
                    state = state,
                    onModeChange = onModeChange,
                    onToggleApp = onToggleApp,
                    onQuery = onQuery,
                )
        }
    }
}

@Composable
private fun LoadingBody(padding: PaddingValues) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(modifier = Modifier.testTag(SplitTunnelTestTags.LOADING))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContentBody(
    padding: PaddingValues,
    state: SplitTunnelUiState.Content,
    onModeChange: (SplitTunnelMode) -> Unit,
    onToggleApp: (String, Boolean) -> Unit,
    onQuery: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
    ) {
        ModeSegment(
            mode = state.mode,
            onModeChange = onModeChange,
        )
        OutlinedTextField(
            value = state.query,
            onValueChange = onQuery,
            label = { Text(stringResource(R.string.split_tunnel_search_hint)) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .testTag(SplitTunnelTestTags.SEARCH),
        )
        if (state.apps.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
                    .testTag(SplitTunnelTestTags.EMPTY),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.split_tunnel_no_apps),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(SplitTunnelTestTags.APPS_LIST),
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                items(state.apps, key = { it.packageName }) { app ->
                    AppRowView(app = app, onToggleApp = onToggleApp)
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModeSegment(
    mode: SplitTunnelMode,
    onModeChange: (SplitTunnelMode) -> Unit,
) {
    SingleChoiceSegmentedButtonRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        SplitTunnelMode.entries.forEachIndexed { index, value ->
            SegmentedButton(
                selected = value == mode,
                onClick = { onModeChange(value) },
                shape = SegmentedButtonDefaults.itemShape(
                    index = index,
                    count = SplitTunnelMode.entries.size,
                ),
                modifier = Modifier.testTag(SplitTunnelTestTags.MODE_SEGMENT_PREFIX + value.name),
            ) {
                Text(stringResource(value.shortLabelRes()))
            }
        }
    }
}

@Composable
private fun AppRowView(
    app: AppRow,
    onToggleApp: (String, Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag(SplitTunnelTestTags.APP_ROW_PREFIX + app.packageName),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.fillMaxWidth(0.85f)) {
            Text(text = app.label, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = app.packageName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
            if (app.isSystem) {
                Text(
                    text = stringResource(R.string.split_tunnel_system_app),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
        }
        Checkbox(
            checked = app.included,
            onCheckedChange = { value -> onToggleApp(app.packageName, value) },
        )
    }
}

@androidx.annotation.StringRes
private fun SplitTunnelMode.shortLabelRes(): Int =
    when (this) {
        SplitTunnelMode.ALL -> R.string.split_tunnel_short_all
        SplitTunnelMode.BYPASS_LAN -> R.string.split_tunnel_short_bypass_lan
        SplitTunnelMode.ALLOWLIST -> R.string.split_tunnel_short_allowlist
        SplitTunnelMode.BLOCKLIST -> R.string.split_tunnel_short_blocklist
    }
