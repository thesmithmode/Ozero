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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.ozero.app.R
import ru.ozero.enginemasterdns.deploy.MasterDnsDeployState

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
            DeployCard(
                state = state,
                onDeployClick = viewModel::onDeployClick,
                onUndeployClick = viewModel::onUndeployClick,
                onDeployReset = viewModel::onDeployReset,
            )
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
private fun DeployCard(
    state: MasterDnsSettingsState,
    onDeployClick: (host: String, port: Int, login: String, password: CharArray) -> Unit,
    onUndeployClick: (host: String, port: Int, login: String, password: CharArray) -> Unit,
    onDeployReset: () -> Unit,
) {
    var host by rememberSaveable { mutableStateOf(state.serverIp) }
    var portText by rememberSaveable {
        mutableStateOf(if (state.serverPort != 22) state.serverPort.toString() else "22")
    }
    var login by rememberSaveable { mutableStateOf("root") }
    var password by rememberSaveable { mutableStateOf("") }

    val deployState = state.deployState
    val isDeploying = deployState is MasterDnsDeployState.Connecting ||
        deployState is MasterDnsDeployState.CheckingPreflight ||
        deployState is MasterDnsDeployState.InstallingDocker ||
        deployState is MasterDnsDeployState.BuildingImage ||
        deployState is MasterDnsDeployState.StartingContainer ||
        deployState is MasterDnsDeployState.ExtractingKey ||
        deployState is MasterDnsDeployState.Removing
    val isDone = deployState is MasterDnsDeployState.Done
    val isRemoved = deployState is MasterDnsDeployState.Removed

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = stringResource(R.string.masterdns_deploy_section_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(R.string.masterdns_vps_requirements),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(8.dp),
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = host,
                onValueChange = { host = it },
                label = { Text(stringResource(R.string.masterdns_deploy_host_label)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                enabled = !isDeploying,
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = portText,
                    onValueChange = { portText = it },
                    label = { Text(stringResource(R.string.masterdns_deploy_port_label)) },
                    modifier = Modifier.weight(0.35f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    enabled = !isDeploying,
                )
                OutlinedTextField(
                    value = login,
                    onValueChange = { login = it },
                    label = { Text(stringResource(R.string.masterdns_deploy_login_label)) },
                    modifier = Modifier.weight(0.65f),
                    singleLine = true,
                    enabled = !isDeploying,
                )
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text(stringResource(R.string.masterdns_deploy_password_label)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                enabled = !isDeploying,
            )
            Spacer(Modifier.height(12.dp))
            when {
                isDeploying -> DeployProgressRow(deployState)
                isDone -> DeployDoneRow(
                    onRedeploy = {
                        onDeployReset()
                    },
                    onUndeploy = {
                        val port = portText.toIntOrNull() ?: 22
                        onUndeployClick(host, port, login, password.toCharArray())
                        password = ""
                    },
                    enabled = host.isNotBlank() && login.isNotBlank(),
                )
                isRemoved -> DeployRemovedRow(onReset = onDeployReset)
                deployState is MasterDnsDeployState.Error -> DeployErrorRow(
                    error = deployState,
                    onRetry = { onDeployReset() },
                )
                else -> Button(
                    onClick = {
                        val port = portText.toIntOrNull() ?: 22
                        onDeployClick(host, port, login, password.toCharArray())
                        password = ""
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = host.isNotBlank() && login.isNotBlank(),
                ) {
                    Text(stringResource(R.string.masterdns_deploy_button))
                }
            }
        }
    }
}

@Composable
private fun DeployProgressRow(state: MasterDnsDeployState) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        Spacer(Modifier.width(8.dp))
        Text(
            text = deployStateLabel(state),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun DeployDoneRow(
    onRedeploy: () -> Unit,
    onUndeploy: () -> Unit,
    enabled: Boolean,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.masterdns_deploy_state_done),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onRedeploy, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.masterdns_redeploy_button))
            }
            TextButton(
                onClick = onUndeploy,
                modifier = Modifier.weight(1f),
                enabled = enabled,
            ) {
                Text(stringResource(R.string.masterdns_remove_button))
            }
        }
    }
}

@Composable
private fun DeployRemovedRow(onReset: () -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.masterdns_deploy_state_removed),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onReset, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.masterdns_deploy_retry_button))
        }
    }
}

@Composable
private fun DeployErrorRow(
    error: MasterDnsDeployState.Error,
    onRetry: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(
                text = deployErrorMessage(error.message),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            TextButton(onClick = onRetry) {
                Text(stringResource(R.string.masterdns_deploy_retry_button))
            }
        }
    }
}

@Composable
private fun deployStateLabel(state: MasterDnsDeployState): String = when (state) {
    is MasterDnsDeployState.Connecting -> stringResource(R.string.masterdns_deploy_state_connecting)
    is MasterDnsDeployState.CheckingPreflight -> stringResource(R.string.masterdns_deploy_state_preflight)
    is MasterDnsDeployState.InstallingDocker -> stringResource(R.string.masterdns_deploy_state_installing_docker)
    is MasterDnsDeployState.BuildingImage -> stringResource(R.string.masterdns_deploy_state_building_image)
    is MasterDnsDeployState.StartingContainer -> stringResource(R.string.masterdns_deploy_state_starting_container)
    is MasterDnsDeployState.ExtractingKey -> stringResource(R.string.masterdns_deploy_state_extracting_key)
    is MasterDnsDeployState.Removing -> stringResource(R.string.masterdns_deploy_state_removing)
    else -> ""
}

@Composable
private fun deployErrorMessage(code: String): String = when (code) {
    "port_53_busy" -> stringResource(R.string.masterdns_deploy_error_port_busy)
    "insufficient_resources" -> stringResource(R.string.masterdns_deploy_error_resources)
    "docker_install_failed" -> stringResource(R.string.masterdns_deploy_error_docker)
    "dpkg_locked" -> stringResource(R.string.masterdns_deploy_error_dpkg_locked)
    "sudo_not_installed" -> stringResource(R.string.masterdns_deploy_error_sudo_not_installed)
    "sudo_pwd_required" -> stringResource(R.string.masterdns_deploy_error_sudo_pwd_required)
    "sudo_not_allowed" -> stringResource(R.string.masterdns_deploy_error_sudo_not_allowed)
    "sudo_no_home" -> stringResource(R.string.masterdns_deploy_error_sudo_no_home)
    "sudo_not_in_group" -> stringResource(R.string.masterdns_deploy_error_sudo_not_in_group)
    "build_failed" -> stringResource(R.string.masterdns_deploy_error_build)
    "run_failed" -> stringResource(R.string.masterdns_deploy_error_run)
    "key_extraction_failed" -> stringResource(R.string.masterdns_deploy_error_key)
    "auth_failed" -> stringResource(R.string.masterdns_deploy_error_auth)
    "connection_failed" -> stringResource(R.string.masterdns_deploy_error_connection)
    "remove_failed" -> stringResource(R.string.masterdns_deploy_error_remove)
    "unexpected_error" -> stringResource(R.string.masterdns_deploy_error_unexpected)
    else -> stringResource(R.string.masterdns_deploy_error_generic, code)
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
