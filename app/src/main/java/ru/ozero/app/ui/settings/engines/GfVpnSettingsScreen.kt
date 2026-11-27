package ru.ozero.app.ui.settings.engines

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ru.ozero.app.R
import ru.ozero.enginegfvpn.GfVpnBundledSdk

@Composable
fun GfVpnSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val available = remember(context) { GfVpnBundledSdk.isAvailable(context) }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedButton(onClick = onBack) { Text(stringResource(R.string.gfvpn_back)) }
        Text("gfVPN", style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.gfvpn_summary))
        Text(stringResource(R.string.gfvpn_trust_warning), style = MaterialTheme.typography.bodySmall)
        Text(stringResource(if (available) R.string.gfvpn_installed else R.string.gfvpn_missing))
    }
}
