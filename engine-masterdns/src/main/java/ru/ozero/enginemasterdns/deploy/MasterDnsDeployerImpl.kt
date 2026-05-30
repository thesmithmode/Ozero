package ru.ozero.enginemasterdns.deploy

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import ru.ozero.enginescore.PersistentLoggers

internal class MasterDnsDeployerImpl(
    private val transport: SshTransport,
) : MasterDnsServerDeployer {

    override fun deploy(credentials: MasterDnsDeployCredentials): Flow<MasterDnsDeployState> = flow {
        PersistentLoggers.debug(TAG, "deploy: start host=${credentials.host}:${credentials.port}")
        if (!connectAndAuth(credentials, "deploy")) return@flow
        try {
            if (!preflightChecks()) return@flow
            if (!installDocker()) return@flow
            if (!buildAndRun()) return@flow
            val key = extractKey() ?: return@flow
            PersistentLoggers.debug(TAG, "deploy: done host=${credentials.host} key_len=${key.length}")
            emit(MasterDnsDeployState.Done(buildClientToml(credentials.host, key)))
        } catch (e: Exception) {
            PersistentLoggers.warn(TAG, "deploy: unexpected error", e)
            emit(MasterDnsDeployState.Error("unexpected_error"))
        } finally {
            transport.close()
        }
    }

    override fun undeploy(credentials: MasterDnsDeployCredentials): Flow<MasterDnsDeployState> = flow {
        if (!connectAndAuth(credentials, "undeploy")) return@flow
        try {
            emit(MasterDnsDeployState.Removing)
            val result = transport.exec(MasterDnsDockerScripts.removeAll)
            if (!result.contains(MasterDnsDockerScripts.MARKER_REMOVE_OK)) {
                emit(MasterDnsDeployState.Error("remove_failed"))
                return@flow
            }
            emit(MasterDnsDeployState.Removed)
        } catch (e: Exception) {
            PersistentLoggers.warn(TAG, "undeploy: unexpected error", e)
            emit(MasterDnsDeployState.Error("unexpected_error"))
        } finally {
            transport.close()
        }
    }

    private suspend fun FlowCollector<MasterDnsDeployState>.connectAndAuth(
        credentials: MasterDnsDeployCredentials,
        tag: String,
    ): Boolean {
        emit(MasterDnsDeployState.Connecting)
        val connected = try {
            transport.connect(credentials.host, credentials.port)
            true
        } catch (e: Exception) {
            PersistentLoggers.warn(TAG, "$tag: connection failed", e)
            transport.close()
            emit(MasterDnsDeployState.Error("connection_failed"))
            false
        }
        if (!connected) {
            credentials.clear()
            return false
        }
        val authed = try {
            transport.auth(credentials.login, credentials.password)
            true
        } catch (e: Exception) {
            PersistentLoggers.warn(TAG, "$tag: auth failed", e)
            transport.close()
            emit(MasterDnsDeployState.Error("auth_failed"))
            false
        } finally {
            credentials.clear()
        }
        return authed
    }

    private suspend fun FlowCollector<MasterDnsDeployState>.preflightChecks(): Boolean {
        PersistentLoggers.debug(TAG, "deploy: sudo check")
        val sudoResult = transport.exec(MasterDnsDockerScripts.checkSudoNoPassword)
        PersistentLoggers.debug(TAG, "deploy: sudo result=${sudoResult.takeShort()}")
        val sudoError = mapSudoResult(sudoResult)
        if (sudoError != null) {
            emit(MasterDnsDeployState.Error(sudoError))
            return false
        }
        emit(MasterDnsDeployState.CheckingPreflight)
        PersistentLoggers.debug(TAG, "deploy: port 53 check")
        val portResult = transport.exec(MasterDnsDockerScripts.checkPort53)
        PersistentLoggers.debug(TAG, "deploy: port result=${portResult.takeShort()}")
        val portBusy = parsePortBusy(portResult)
        if (portBusy != null) {
            if (portBusy.owner == AMNEZIA_DNS_OWNER) {
                transport.exec(MasterDnsDockerScripts.removeAmneziaDnsContainer)
                val retryResult = transport.exec(MasterDnsDockerScripts.checkPort53)
                PersistentLoggers.debug(TAG, "deploy: port retry result=${retryResult.takeShort()}")
                val retryPortBusy = parsePortBusy(retryResult)
                if (retryPortBusy != null) {
                    emit(retryPortBusy)
                    return false
                }
                if (retryResult.contains(MasterDnsDockerScripts.MARKER_PORT_BUSY)) {
                    emit(MasterDnsDeployState.Error("port_53_busy"))
                    return false
                }
            } else {
                emit(portBusy)
                return false
            }
        } else if (portResult.contains(MasterDnsDockerScripts.MARKER_PORT_BUSY)) {
            emit(MasterDnsDeployState.Error("port_53_busy"))
            return false
        }
        return checkResources()
    }

    private fun parsePortBusy(result: String): MasterDnsDeployState.PortBusy? = result
        .lineSequence()
        .firstOrNull { it.startsWith("${MasterDnsDockerScripts.MARKER_PORT_BUSY}|") }
        ?.let { line ->
            val fields = line.substringAfter('|')
                .split('|')
                .mapNotNull { part ->
                    val key = part.substringBefore('=', missingDelimiterValue = "")
                    val value = part.substringAfter('=', missingDelimiterValue = "")
                    if (key.isBlank() || value.isBlank()) null else key to value
                }
                .toMap()
            val protocol = fields["proto"]?.trim()?.lowercase().orEmpty()
            val address = fields["addr"]?.trim().orEmpty()
            val owner = fields["owner"]?.trim().orEmpty()
            if (protocol.isBlank() || address.isBlank() || owner.isBlank()) {
                null
            } else {
                MasterDnsDeployState.PortBusy(protocol = protocol, address = address, owner = owner)
            }
        }

    private suspend fun FlowCollector<MasterDnsDeployState>.checkResources(): Boolean {
        val resources = transport.exec(MasterDnsDockerScripts.checkResources)
        val parts = resources.trim().split(" ")
        val freeMem = parts.getOrNull(0)?.trim()?.toIntOrNull() ?: 0
        val freeDisk = parts.getOrNull(1)?.trim()?.toIntOrNull() ?: 0
        PersistentLoggers.debug(TAG, "deploy: resources freeMem=${freeMem}MB freeDisk=${freeDisk}MB")
        if (freeMem < MasterDnsDockerScripts.MIN_FREE_RAM_MB || freeDisk < MasterDnsDockerScripts.MIN_FREE_DISK_MB) {
            emit(MasterDnsDeployState.Error("insufficient_resources"))
            return false
        }
        return true
    }

    private suspend fun FlowCollector<MasterDnsDeployState>.installDocker(): Boolean {
        emit(MasterDnsDeployState.InstallingDocker)
        PersistentLoggers.debug(TAG, "deploy: installing docker (timeout 360s)")
        val dockerResult = transport.exec(MasterDnsDockerScripts.installDocker, timeoutMs = 360_000L)
        PersistentLoggers.debug(TAG, "deploy: docker install result=${dockerResult.takeShort()}")
        if (dockerResult.contains(MasterDnsDockerScripts.MARKER_ERR_DPKG_LOCKED)) {
            emit(MasterDnsDeployState.Error("dpkg_locked"))
            return false
        }
        if (!dockerResult.contains(MasterDnsDockerScripts.MARKER_DOCKER_OK)) {
            emit(MasterDnsDeployState.Error("docker_install_failed"))
            return false
        }
        return true
    }

    private suspend fun FlowCollector<MasterDnsDeployState>.buildAndRun(): Boolean {
        emit(MasterDnsDeployState.BuildingImage)
        PersistentLoggers.debug(TAG, "deploy: docker build masterdns-ozero (timeout 300s)")
        val buildResult = transport.exec(MasterDnsDockerScripts.deployMasterDns, timeoutMs = 300_000L)
        PersistentLoggers.debug(TAG, "deploy: build result=${buildResult.takeShort()}")
        if (!buildResult.contains(MasterDnsDockerScripts.MARKER_BUILD_OK)) {
            emit(MasterDnsDeployState.Error("build_failed"))
            return false
        }
        emit(MasterDnsDeployState.StartingContainer)
        PersistentLoggers.debug(TAG, "deploy: docker run masterdns-ozero -p 53:53/udp")
        val runResult = transport.exec(MasterDnsDockerScripts.runContainer)
        PersistentLoggers.debug(TAG, "deploy: run result=${runResult.takeShort()}")
        if (!runResult.contains(MasterDnsDockerScripts.MARKER_RUN_OK)) {
            emit(MasterDnsDeployState.Error("run_failed"))
            return false
        }
        delay(CONTAINER_STARTUP_DELAY_MS)
        openFirewall()
        return true
    }

    private suspend fun openFirewall() {
        PersistentLoggers.debug(TAG, "deploy: открываю 53/udp в firewall")
        val fwResult = transport.exec(MasterDnsDockerScripts.openFirewallPort53)
        PersistentLoggers.debug(TAG, "deploy: firewall result=${fwResult.takeShort()}")
        if (!fwResult.contains("FW_") || fwResult.contains(MasterDnsDockerScripts.MARKER_FW_NONE_OK)) {
            PersistentLoggers.warn(
                TAG,
                "deploy: firewall step не открыл 53/udp (result=${fwResult.take(80)}) — продолжаем, " +
                    "юзеру возможно придётся открыть порт вручную",
            )
        }
    }

    private suspend fun FlowCollector<MasterDnsDeployState>.extractKey(): String? {
        emit(MasterDnsDeployState.ExtractingKey)
        PersistentLoggers.debug(TAG, "deploy: читаю encrypt_key из контейнера")
        val key = transport.exec(MasterDnsDockerScripts.readEncryptKey).trim()
        PersistentLoggers.debug(TAG, "deploy: encrypt_key len=${key.length}")
        if (key.isEmpty()) {
            emit(MasterDnsDeployState.Error("key_extraction_failed"))
            return null
        }
        return key
    }

    private fun mapSudoResult(result: String): String? = when {
        result.contains(MasterDnsDockerScripts.MARKER_ERR_SUDO_NOT_INSTALLED) -> "sudo_not_installed"
        result.contains(MasterDnsDockerScripts.MARKER_ERR_SUDO_PWD_REQUIRED) -> "sudo_pwd_required"
        result.contains(MasterDnsDockerScripts.MARKER_ERR_SUDO_NOT_ALLOWED) -> "sudo_not_allowed"
        result.contains(MasterDnsDockerScripts.MARKER_ERR_SUDO_NO_HOME) -> "sudo_no_home"
        result.contains(MasterDnsDockerScripts.MARKER_ERR_SUDO_NOT_IN_GROUP) -> "sudo_not_in_group"
        else -> null
    }

    private fun buildClientToml(serverIp: String, encryptionKey: String): String =
        """
        LISTEN_IP = "127.0.0.1"
        LISTEN_PORT = 1080
        PROTOCOL_TYPE = "SOCKS5"
        DATA_ENCRYPTION_METHOD = 5
        RESOLVER_BALANCING_STRATEGY = 3
        ENCRYPTION_KEY = "$encryptionKey"
        SERVER = "$serverIp"
        DOMAINS = []
        """.trimIndent()

    private fun String.takeShort(maxLen: Int = 160): String =
        replace('\n', ' ').replace('\r', ' ').trim().take(maxLen)

    private companion object {
        const val TAG = "MasterDnsDeployer"
        const val CONTAINER_STARTUP_DELAY_MS = 3_000L
        const val AMNEZIA_DNS_OWNER = "docker:amnezia-dns"
    }
}
