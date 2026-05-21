package ru.ozero.app.ui.settings.engines

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.ozero.app.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MasterDnsSettingsScreen(
    onBack: () -> Unit,
    viewModel: MasterDnsSettingsViewModel = hiltViewModel(),
) {
    BackHandler(onBack = onBack)
    val state by viewModel.state.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val copiedMsg = stringResource(R.string.masterdns_copied_toast)
    val onCopy: (String) -> Unit = { value ->
        clipboard.setText(AnnotatedString(value))
        Toast.makeText(context, copiedMsg, Toast.LENGTH_SHORT).show()
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.masterdns_engine_name))
                        Spacer(Modifier.width(8.dp))
                        BetaBadge()
                    }
                },
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
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.masterdns_enabled),
                    style = MaterialTheme.typography.titleMedium,
                )
                Switch(
                    checked = state.enabled,
                    onCheckedChange = viewModel::onEnabledChange,
                )
            }
            ServerSetupCard(onCopy = onCopy)
            OutlinedTextField(
                value = state.configToml,
                onValueChange = viewModel::onConfigTomlChange,
                label = { Text(stringResource(R.string.masterdns_config_toml)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 6,
            )
            OutlinedTextField(
                value = state.resolversText,
                onValueChange = viewModel::onResolversChange,
                label = { Text(stringResource(R.string.masterdns_resolvers)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 4,
            )
        }
    }
}

@Composable
private fun BetaBadge() {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        shape = RoundedCornerShape(50),
    ) {
        Text(
            text = stringResource(R.string.masterdns_beta_badge),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun ServerSetupCard(onCopy: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.masterdns_setup_title),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = if (expanded) {
                        Icons.Filled.KeyboardArrowUp
                    } else {
                        Icons.Filled.KeyboardArrowDown
                    },
                    contentDescription = null,
                )
            }
            if (expanded) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.masterdns_setup_prereq),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
                Spacer(Modifier.height(16.dp))
                SetupStep(
                    number = 1,
                    title = stringResource(R.string.masterdns_setup_step1_title),
                    code = stringResource(R.string.masterdns_setup_step1_body),
                    onCopy = onCopy,
                )
                Spacer(Modifier.height(12.dp))
                SetupStep(
                    number = 2,
                    title = stringResource(R.string.masterdns_setup_step2_title),
                    code = INSTALL_CMD,
                    onCopy = onCopy,
                )
                Spacer(Modifier.height(12.dp))
                SetupStep(
                    number = 3,
                    title = stringResource(R.string.masterdns_setup_step3_title),
                    code = KEYGEN_CMD,
                    onCopy = onCopy,
                )
                Spacer(Modifier.height(12.dp))
                SetupStep(
                    number = 4,
                    title = stringResource(R.string.masterdns_setup_step4_title),
                    code = stringResource(R.string.masterdns_setup_step4_body),
                    onCopy = onCopy,
                )
                Spacer(Modifier.height(12.dp))
                SetupStep(
                    number = 5,
                    title = stringResource(R.string.masterdns_setup_step5_title),
                    code = START_CMD,
                    onCopy = onCopy,
                )
                Spacer(Modifier.height(12.dp))
                SetupStep(
                    number = 6,
                    title = stringResource(R.string.masterdns_setup_step6_title),
                    code = stringResource(R.string.masterdns_setup_step6_body),
                    onCopy = onCopy,
                )
            }
        }
    }
}

@Composable
private fun SetupStep(
    number: Int,
    title: String,
    code: String,
    onCopy: (String) -> Unit,
) {
    Column {
        Text(
            text = "$number. $title",
            style = MaterialTheme.typography.titleSmall,
        )
        Spacer(Modifier.height(6.dp))
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = code,
                modifier = Modifier.padding(8.dp),
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                ),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = { onCopy(code) }) {
                Text(stringResource(R.string.masterdns_copy_button))
            }
        }
    }
}

private const val INSTALL_CMD =
    "curl -fsSL https://raw.githubusercontent.com/masterking32/MasterDnsVPN/main/" +
        "server_linux_install.sh | bash"

private const val KEYGEN_CMD =
    "openssl rand -hex 32 > /etc/masterdnsvpn/encrypt_key.txt && " +
        "chmod 600 /etc/masterdnsvpn/encrypt_key.txt"

private const val START_CMD = "systemctl enable --now masterdnsvpn-server"
