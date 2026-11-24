package ru.ozero.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import ru.ozero.app.R
import ru.ozero.commonnet.NatObservation
import ru.ozero.commonnet.NetworkDiagnosticsReport

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworkDiagnosticsScreen(
    onBack: () -> Unit,
    viewModel: NetworkDiagnosticsViewModel = hiltViewModel(),
) {
    val canRun by viewModel.canRun.collectAsStateWithLifecycle(initialValue = false)
    val scope = rememberCoroutineScope()
    var report by remember { mutableStateOf<NetworkDiagnosticsReport?>(null) }
    var unavailable by remember { mutableStateOf(false) }
    var running by remember { mutableStateOf(false) }
    var job by remember { mutableStateOf<Job?>(null) }
    LaunchedEffect(canRun) {
        if (!canRun) {
            job?.cancel()
            report = null
            unavailable = false
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.network_diagnostics_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.settings_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.network_diagnostics_warning))
            Button(
                enabled = canRun && !running,
                modifier = Modifier.testTag("network_diagnostics_run"),
                onClick = {
                    running = true
                    report = null
                    unavailable = false
                    job = scope.launch {
                        try {
                            report = viewModel.run()
                            unavailable = report == null
                        } finally {
                            running = false
                        }
                    }
                },
            ) {
                Text(stringResource(R.string.network_diagnostics_run))
            }
            if (!canRun) Text(stringResource(R.string.network_diagnostics_disconnect))
            if (running) CircularProgressIndicator()
            if (unavailable) Text(stringResource(R.string.network_diagnostics_unavailable))
            if (canRun) report?.let { DiagnosticsResults(it) }
        }
    }
}

@Composable
private fun DiagnosticsResults(report: NetworkDiagnosticsReport) {
    val unknown = stringResource(R.string.network_diagnostics_unknown)
    val mtu = report.linkMtu?.let { stringResource(R.string.network_diagnostics_bytes, it) } ?: unknown
    val tcp = stringResource(
        if (report.tcpReachable) R.string.network_diagnostics_ok else R.string.network_diagnostics_no_response,
    )
    val udp = stringResource(
        if (report.nat != null) R.string.network_diagnostics_ok else R.string.network_diagnostics_no_response,
    )
    val nat = when (report.nat) {
        NatObservation.TRANSLATED -> stringResource(R.string.network_diagnostics_translated)
        NatObservation.UNCHANGED -> stringResource(R.string.network_diagnostics_unchanged)
        null -> unknown
    }
    Text(stringResource(R.string.network_diagnostics_mtu, mtu))
    Text(stringResource(R.string.network_diagnostics_tcp, tcp))
    Text(stringResource(R.string.network_diagnostics_udp, udp))
    Text(stringResource(R.string.network_diagnostics_nat, nat))
}
