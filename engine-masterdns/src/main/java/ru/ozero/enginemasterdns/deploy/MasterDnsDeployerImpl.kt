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
            if (!postDockerPortChecks()) return@flow
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

    override fun removeAmneziaDnsAndContinue(
        credentials: MasterDnsDeployCredentials,
    ): Flow<MasterDnsDeployState> = flow {
        PersistentLoggers.debug(TAG, "removeAmneziaDnsAndContinue: start host=${credentials.host}:${credentials.port}")
        if (!connectAndAuth(credentials, "removeAmneziaDnsAndContinue")) return@flow
        try {
            emit(MasterDnsDeployState.Removing)
            val removeResult = transport.exec(MasterDnsDockerScripts.removeAmneziaDnsOnly)
            PersistentLoggers.debug(TAG, "removeAmneziaDnsAndContinue: remove result=${removeResult.takeShort()}")
            if (!removeResult.contains(MasterDnsDockerScripts.MARKER_AMNEZIA_DNS_REMOVED) &&
                !removeResult.contains(MasterDnsDockerScripts.MARKER_AMNEZIA_DNS_NOT_FOUND)
            ) {
                emit(MasterDnsDeployState.Error("amnezia_dns_remove_failed"))
                return@flow
            }
            if (!preflightChecks()) return@flow
            if (!installDocker()) return@flow
            if (!postDockerPortChecks()) return@flow
            if (!buildAndRun()) return@flow
            val key = extractKey() ?: return@flow
            PersistentLoggers.debug(
                TAG,
                "removeAmneziaDnsAndContinue: done host=${credentials.host} key_len=${key.length}",
            )
            emit(MasterDnsDeployState.Done(buildClientToml(credentials.host, key)))
        } catch (e: Exception) {
            PersistentLoggers.warn(TAG, "removeAmneziaDnsAndContinue: unexpected error", e)
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
        if (!checkAmneziaDns53Conflict("deploy: amnezia-dns port 53 check")) return false
        if (!checkPort53Availability("deploy: port 53 check")) return false
        return checkResources()
    }

    private suspend fun FlowCollector<MasterDnsDeployState>.postDockerPortChecks(): Boolean {
        if (!checkAmneziaDns53Conflict("deploy: post-docker amnezia-dns port 53 check")) return false
        return checkPort53Availability("deploy: post-docker port 53 check")
    }

    private suspend fun FlowCollector<MasterDnsDeployState>.checkAmneziaDns53Conflict(logLabel: String): Boolean {
        PersistentLoggers.debug(TAG, logLabel)
        val amneziaResult = transport.exec(MasterDnsDockerScripts.checkAmneziaDns53)
        PersistentLoggers.debug(TAG, "deploy: amnezia-dns result=${amneziaResult.takeShort()}")
        parseAmneziaDnsConflict(amneziaResult)?.let { conflict ->
            emit(conflict)
            return false
        }
        return true
    }

    private suspend fun FlowCollector<MasterDnsDeployState>.checkPort53Availability(logLabel: String): Boolean {
        PersistentLoggers.debug(TAG, logLabel)
        val portResult = transport.exec(MasterDnsDockerScripts.checkPort53)
        PersistentLoggers.debug(TAG, "deploy: port result=${portResult.takeShort()}")
        val portBusy = parsePortBusy(portResult)
        if (portBusy != null) {
            emit(portBusy)
            return false
        }
        val portError = mapPortResult(portResult)
        if (portError != null) {
            emit(MasterDnsDeployState.Error(portError))
            return false
        }
        return true
    }

    private fun parsePortBusy(result: String): MasterDnsDeployState.PortBusy? = result
        .lineSequence()
        .map { it.trim() }
        .firstOrNull { it.startsWith(PORT_BUSY_PREFIX) }
        ?.let { line ->
            val fields = line.substringAfter('|', missingDelimiterValue = "")
                .split('|')
                .mapNotNull { part ->
                    val key = part.substringBefore('=', missingDelimiterValue = "").trim()
                    val value = part.substringAfter('=', missingDelimiterValue = "").trim()
                    if (key.isBlank() || value.isBlank()) null else key to value
                }
                .toMap()
            val protocol = fields["proto"]?.lowercase().orEmpty()
            val address = fields["addr"].orEmpty()
            val owner = fields["owner"] ?: fields["name"].orEmpty()
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
            emit(MasterDnsDeployState.Error(mapBuildError(buildResult)))
            return false
        }
        emit(MasterDnsDeployState.StartingContainer)
        PersistentLoggers.debug(TAG, "deploy: docker run masterdns-ozero -p 53:53/udp")
        val runResult = transport.exec(MasterDnsDockerScripts.runContainer)
        PersistentLoggers.debug(TAG, "deploy: run result=${runResult.takeShort()}")
        if (!runResult.contains(MasterDnsDockerScripts.MARKER_RUN_OK)) {
            emit(MasterDnsDeployState.Error(mapRunError(runResult)))
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

    private fun parseAmneziaDnsConflict(result: String): MasterDnsDeployState.AmneziaDnsConflict? {
        if (!result.contains(MasterDnsDockerScripts.MARKER_AMNEZIA_DNS_CONFLICT)) return null
        return MasterDnsDeployState.AmneziaDnsConflict(
            protocol = markerValue(result, "proto").ifBlank { "unknown" },
            address = markerValue(result, "addr").ifBlank { "0.0.0.0" },
        )
    }

    private fun markerValue(result: String, key: String): String =
        result.split('|')
            .firstOrNull { it.startsWith("$key=") }
            ?.substringAfter('=')
            .orEmpty()
            .trim()

    private fun mapSudoResult(result: String): String? = when {
        result.contains(MasterDnsDockerScripts.MARKER_ERR_SUDO_NOT_INSTALLED) -> "sudo_not_installed"
        result.contains(MasterDnsDockerScripts.MARKER_ERR_SUDO_PWD_REQUIRED) -> "sudo_pwd_required"
        result.contains(MasterDnsDockerScripts.MARKER_ERR_SUDO_NOT_ALLOWED) -> "sudo_not_allowed"
        result.contains(MasterDnsDockerScripts.MARKER_ERR_SUDO_NO_HOME) -> "sudo_no_home"
        result.contains(MasterDnsDockerScripts.MARKER_ERR_SUDO_NOT_IN_GROUP) -> "sudo_not_in_group"
        else -> null
    }

    private fun mapBuildError(result: String): String = when {
        result.contains(MasterDnsDockerScripts.MARKER_ERR_BUILD_BIN_MISSING) ||
            result.contains("reason=bin_missing") -> {
            val diagnostics = result.lineSequence()
                .map { it.trim() }
                .firstOrNull {
                    it.startsWith("ERR_BUILD|reason=bin_missing") ||
                        it.startsWith(MasterDnsDockerScripts.MARKER_ERR_BUILD_BIN_MISSING)
                }
                ?.sanitizeDeployDiagnostic()
                .orEmpty()
            if (diagnostics.isBlank()) {
                "build_failed/bin_missing"
            } else {
                "build_failed/bin_missing|$diagnostics"
            }
        }
        else -> "build_failed"
    }

    private fun mapRunError(result: String): String {
        val markerLine = result.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.startsWith(MasterDnsDockerScripts.MARKER_ERR_RUN) }
            ?: return "run_failed"
        val details = markerLine.substringAfter('|', missingDelimiterValue = "")
        return if (details.isBlank()) "run_failed" else "run_failed|$details"
    }

    private fun mapPortResult(result: String): String? {
        val busyLine = result.lineSequence()
            .map { it.trim() }
            .firstOrNull { it == MasterDnsDockerScripts.MARKER_PORT_BUSY || it.startsWith(PORT_BUSY_PREFIX) }
            ?: return null
        val details = busyLine.substringAfter('|', missingDelimiterValue = "")
        return if (details.isBlank()) {
            "port_53_busy"
        } else {
            "port_53_busy|$details"
        }
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

    private fun String.takeShort(maxLen: Int = 1_200): String {
        val normalized = replace('\n', ' ').replace('\r', ' ').trim()
        if (normalized.length <= maxLen) return normalized
        val safeMaxLen = maxLen.coerceAtLeast(80)
        val headLen = 360.coerceAtMost(safeMaxLen / 2)
        val tailLen = (safeMaxLen - headLen - 5).coerceAtLeast(0)
        return normalized.take(headLen) + " ... " + normalized.takeLast(tailLen)
    }

    private companion object {
        const val TAG = "MasterDnsDeployer"
        const val CONTAINER_STARTUP_DELAY_MS = 3_000L
        const val PORT_BUSY_PREFIX = "PORT_BUSY|"
    }
}

private fun String.sanitizeDeployDiagnostic(maxLen: Int = 700): String =
    replace('\n', ' ')
        .replace('\r', ' ')
        .replace(Regex("""password=[^| ]+"""), "password=<redacted>")
        .replace(Regex("""token=[^| ]+"""), "token=<redacted>")
        .take(maxLen)
