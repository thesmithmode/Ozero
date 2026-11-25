package ru.ozero.app.ui.settings.engines

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.ozero.app.R
import ru.ozero.enginegfvpn.GfVpnImport

@Composable
fun GfVpnSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    var installed by remember { mutableStateOf(GfVpnImport.isInstalled(context)) }
    var importing by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && !importing && !installed) {
            importing = true
            failed = false
            scope.launch {
                try {
                    withContext(Dispatchers.IO) { GfVpnImport.install(context, uri) }
                    installed = GfVpnImport.isInstalled(context)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    failed = true
                } finally {
                    importing = false
                }
            }
        }
    }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedButton(onClick = onBack) { Text(stringResource(R.string.gfvpn_back)) }
        Text("gfVPN", style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.gfvpn_summary))
        Text(stringResource(R.string.gfvpn_trust_warning), style = MaterialTheme.typography.bodySmall)
        Text(stringResource(if (installed) R.string.gfvpn_installed else R.string.gfvpn_missing))
        if (!installed) {
            Button(onClick = { picker.launch(arrayOf("*/*")) }, enabled = !importing) {
                Text(stringResource(if (importing) R.string.gfvpn_importing else R.string.gfvpn_import))
            }
        }
        if (failed) Text(stringResource(R.string.gfvpn_import_failed), color = MaterialTheme.colorScheme.error)
    }
}
