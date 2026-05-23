package ru.ozero.app.ui.settings.engines

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.ozero.enginemasterdns.MasterDnsConfigStore
import ru.ozero.enginemasterdns.deploy.MasterDnsDeployCredentials
import ru.ozero.enginemasterdns.deploy.MasterDnsDeployState
import ru.ozero.enginemasterdns.deploy.MasterDnsServerDeployer
import ru.ozero.enginescore.PersistentLoggers
import javax.inject.Inject

data class MasterDnsSettingsState(
    val enabled: Boolean = false,
    val configToml: String = "",
    val resolversText: String = "",
    val serverIp: String = "",
    val serverPort: Int = 22,
    val deployState: MasterDnsDeployState = MasterDnsDeployState.Idle,
)

@HiltViewModel
class MasterDnsSettingsViewModel @Inject constructor(
    private val store: MasterDnsConfigStore,
    private val deployer: MasterDnsServerDeployer,
) : ViewModel() {

    private val deployState = MutableStateFlow<MasterDnsDeployState>(MasterDnsDeployState.Idle)

    val state: StateFlow<MasterDnsSettingsState> = combine(
        store.config(),
        deployState,
    ) { cfg, deploy ->
        MasterDnsSettingsState(
            enabled = cfg.enabled,
            configToml = cfg.configToml,
            resolversText = cfg.resolvers.joinToString("\n"),
            serverIp = cfg.serverIp,
            serverPort = cfg.serverPort,
            deployState = deploy,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, MasterDnsSettingsState())

    fun onEnabledChange(enabled: Boolean) {
        viewModelScope.launch {
            runCatching { store.setEnabled(enabled) }
                .onFailure { PersistentLoggers.error(TAG, "setEnabled failed: ${it.message}", it) }
        }
    }

    fun onConfigTomlChange(toml: String) {
        viewModelScope.launch {
            runCatching { store.setConfigToml(toml) }
                .onFailure { PersistentLoggers.error(TAG, "setConfigToml failed: ${it.message}", it) }
        }
    }

    fun onResolversChange(text: String) {
        val list = text.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
        viewModelScope.launch {
            runCatching { store.setResolvers(list) }
                .onFailure { PersistentLoggers.error(TAG, "setResolvers failed: ${it.message}", it) }
        }
    }

    fun onDeployClick(host: String, port: Int, login: String, password: CharArray) {
        val credentials = MasterDnsDeployCredentials(
            host = host,
            port = port,
            login = login,
            password = password,
        )
        viewModelScope.launch {
            deployer.deploy(credentials).collect { deployStep ->
                deployState.value = deployStep
                if (deployStep is MasterDnsDeployState.Done) {
                    runCatching {
                        store.setConfigToml(deployStep.configToml)
                        store.setServerIp(host)
                        store.setServerPort(port)
                    }.onFailure { PersistentLoggers.error(TAG, "deploy persist failed: ${it.message}", it) }
                }
            }
        }
    }

    fun onDeployReset() {
        deployState.value = MasterDnsDeployState.Idle
    }

    private companion object {
        const val TAG = "MasterDnsSettingsVM"
    }
}
