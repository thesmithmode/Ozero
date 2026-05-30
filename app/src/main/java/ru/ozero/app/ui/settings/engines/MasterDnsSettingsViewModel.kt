package ru.ozero.app.ui.settings.engines

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
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
    val configToml: String = "",
    val resolversText: String = "",
    val serverIp: String = "",
    val serverPort: Int = 22,
    val deployState: MasterDnsDeployState = MasterDnsDeployState.Idle,
    val deployLog: List<String> = emptyList(),
)

@HiltViewModel
class MasterDnsSettingsViewModel @Inject constructor(
    private val store: MasterDnsConfigStore,
    private val deployer: MasterDnsServerDeployer,
) : ViewModel() {

    private val deployState = MutableStateFlow<MasterDnsDeployState>(MasterDnsDeployState.Idle)
    private val deployLog = MutableStateFlow<List<String>>(emptyList())
    private var deployJob: Job? = null
    private var pendingDeploy: PendingDeploy? = null

    val state: StateFlow<MasterDnsSettingsState> = combine(
        store.config(),
        deployState,
        deployLog,
    ) { cfg, deploy, log ->
        MasterDnsSettingsState(
            configToml = cfg.configToml,
            resolversText = cfg.resolvers.joinToString("\n"),
            serverIp = cfg.serverIp,
            serverPort = cfg.serverPort,
            deployState = deploy,
            deployLog = log,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, MasterDnsSettingsState())

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
        if (deployJob?.isActive == true) return
        pendingDeploy?.clear()
        pendingDeploy = PendingDeploy(host, port, login, password.copyOf())
        password.fill('\u0000')
        deployLog.value = emptyList()
        appendLog("→ Подключение к $host:$port")
        startDeployJob(host) { deployer.deploy(it) }
    }

    fun onUndeployClick(host: String, port: Int, login: String, password: CharArray) {
        if (deployJob?.isActive == true) return
        val credentials = MasterDnsDeployCredentials(
            host = host,
            port = port,
            login = login,
            password = password.copyOf(),
        )
        password.fill('\u0000')
        deployLog.value = emptyList()
        appendLog("→ Подключение к $host:$port (удаление)")
        deployJob = viewModelScope.launch {
            runCatching {
                deployer.undeploy(credentials).collect { step ->
                    deployState.value = step
                    logFor(step, host = host)?.let(::appendLog)
                    if (step is MasterDnsDeployState.Removed) {
                        runCatching {
                            store.setServerIp("")
                            store.setServerPort(22)
                        }.onFailure { PersistentLoggers.error(TAG, "undeploy persist failed: ${it.message}", it) }
                    }
                }
            }.onFailure {
                PersistentLoggers.error(TAG, "undeploy flow threw: ${it.message}", it)
                deployState.value = MasterDnsDeployState.Error("unexpected_error")
                appendLog("✗ Неожиданная ошибка: ${it.message}")
            }
        }
    }

    fun onAmneziaDnsConflictCancel() {
        deployJob?.cancel()
        deployJob = null
        clearPendingDeploy()
        deployState.value = MasterDnsDeployState.Error("amnezia_dns_cancelled")
        appendLog("✗ Деплой отменён: найден контейнер amnezia-dns на порту 53")
    }

    fun onAmneziaDnsRemoveAndContinue() {
        if (deployJob?.isActive == true) return
        val pending = pendingDeploy ?: return
        appendLog("→ Удаление только контейнера amnezia-dns и повторная проверка порта 53")
        startDeployJob(pending.host) { deployer.removeAmneziaDnsAndContinue(it) }
        clearPendingDeploy()
    }

    fun onDeployReset() {
        deployJob?.cancel()
        deployJob = null
        clearPendingDeploy()
        deployState.value = MasterDnsDeployState.Idle
        deployLog.value = emptyList()
    }

    private fun startDeployJob(
        host: String,
        deployFlow: (MasterDnsDeployCredentials) -> kotlinx.coroutines.flow.Flow<MasterDnsDeployState>,
    ) {
        val pending = pendingDeploy ?: return
        val credentials = pending.credentials()
        deployJob = viewModelScope.launch {
            runCatching {
                deployFlow(credentials).collect { deployStep ->
                    deployState.value = deployStep
                    logFor(deployStep, host = host)?.let(::appendLog)
                    if (deployStep is MasterDnsDeployState.Done) {
                        persistDeployResult(host, pending.port, deployStep.configToml)
                    }
                    if (
                        deployStep is MasterDnsDeployState.Done ||
                        deployStep is MasterDnsDeployState.Error ||
                        deployStep is MasterDnsDeployState.PortBusy
                    ) {
                        clearPendingDeploy()
                    }
                }
            }.onFailure {
                PersistentLoggers.error(TAG, "deploy flow threw: ${it.message}", it)
                deployState.value = MasterDnsDeployState.Error("unexpected_error")
                appendLog("✗ Неожиданная ошибка: ${it.message}")
                clearPendingDeploy()
            }
        }
    }

    private suspend fun persistDeployResult(host: String, port: Int, configToml: String) {
        runCatching {
            store.setConfigToml(configToml)
            store.setServerIp(host)
            store.setServerPort(port)
            store.setResolvers(listOf("$host:$MASTERDNS_DNS_PORT"))
        }.onFailure { PersistentLoggers.error(TAG, "deploy persist failed: ${it.message}", it) }
        appendLog("✓ Резолверы и client_config записаны автоматически")
    }

    private fun clearPendingDeploy() {
        pendingDeploy?.clear()
        pendingDeploy = null
    }

    private fun appendLog(line: String) {
        val cur = deployLog.value
        val trimmed = if (cur.size >= MAX_LOG_LINES) cur.drop(cur.size - MAX_LOG_LINES + 1) else cur
        deployLog.value = trimmed + line
    }

    private fun logFor(step: MasterDnsDeployState, host: String): String? = when (step) {
        is MasterDnsDeployState.Connecting -> null
        is MasterDnsDeployState.CheckingPreflight ->
            "→ Проверка sudo, порта 53/udp, RAM и диска на $host"
        is MasterDnsDeployState.InstallingDocker ->
            "→ Установка Docker (apt/dnf/yum/zypper/pacman) — до 6 минут"
        is MasterDnsDeployState.BuildingImage ->
            "→ Сборка образа masterdns-ozero из ubuntu:22.04 + masterdnsvpn-server"
        is MasterDnsDeployState.StartingContainer ->
            "→ Запуск контейнера на 53/udp, генерация encrypt_key, открытие firewall"
        is MasterDnsDeployState.ExtractingKey ->
            "→ Извлечение encrypt_key из контейнера"
        is MasterDnsDeployState.AmneziaDnsConflict ->
            "✗ Порт 53 занят amnezia-dns (${step.protocol}, ${step.address})"
        is MasterDnsDeployState.Done -> "✓ Сервер развёрнут"
        is MasterDnsDeployState.Removing -> "→ Удаление контейнера и образа"
        is MasterDnsDeployState.Removed -> "✓ Сервер полностью удалён"
        is MasterDnsDeployState.PortBusy -> "✗ Порт ${step.protocol}/${step.address} занят: ${step.owner}"
        is MasterDnsDeployState.Error -> "✗ Ошибка: ${step.message}"
        is MasterDnsDeployState.Idle -> null
    }

    private data class PendingDeploy(
        val host: String,
        val port: Int,
        val login: String,
        val password: CharArray,
    ) {
        fun credentials(): MasterDnsDeployCredentials = MasterDnsDeployCredentials(host, port, login, password.copyOf())

        fun clear() {
            password.fill('\u0000')
        }
    }

    private companion object {
        const val TAG = "MasterDnsSettingsVM"
        const val MASTERDNS_DNS_PORT = 53
        const val MAX_LOG_LINES = 50
    }
}
