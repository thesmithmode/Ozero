package ru.ozero.app.ui.settings.engines

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.ozero.app.R
import ru.ozero.enginescore.EngineId
import ru.ozero.engineopera.OperaRegion
import ru.ozero.engineopera.OperaServerSelection

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OperaEngineSettingsScreen(
    onBack: () -> Unit,
    viewModel: OperaEngineSettingsViewModel = hiltViewModel(),
) {
    val model by viewModel.settings.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Opera Proxy") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.settings_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.settings_opera_summary), style = MaterialTheme.typography.bodyMedium)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.masque_auto_enabled), modifier = Modifier.weight(1f))
                Switch(
                    checked = EngineId.OPERA in model.engineAutoPriority,
                    onCheckedChange = viewModel::setAutoEnabled,
                )
            }
            HorizontalDivider()
            Text(stringResource(R.string.opera_region), style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OperaRegion.entries.forEach { region ->
                    val label = stringResource(
                        when (region) {
                            OperaRegion.EU -> R.string.opera_region_europe
                            OperaRegion.AS -> R.string.opera_region_asia
                            OperaRegion.AM -> R.string.opera_region_americas
                        },
                    )
                    FilterChip(
                        selected = model.operaRegion == region.name,
                        onClick = { viewModel.selectRegion(region.name) },
                        label = { Text(label) },
                    )
                }
            }
            Text(stringResource(R.string.opera_selection), style = MaterialTheme.typography.titleMedium)
            Column(modifier = Modifier.fillMaxWidth()) {
                OperaServerSelection.entries.forEach { selection ->
                    FilterChip(
                        selected = model.operaSelection == selection.argument,
                        onClick = { viewModel.selectServer(selection.argument) },
                        label = {
                            Text(
                                stringResource(
                                    when (selection) {
                                        OperaServerSelection.FIRST -> R.string.opera_first
                                        OperaServerSelection.RANDOM -> R.string.opera_random
                                        OperaServerSelection.FASTEST -> R.string.opera_fastest
                                    },
                                ),
                            )
                        },
                    )
                }
            }
        }
    }
}
