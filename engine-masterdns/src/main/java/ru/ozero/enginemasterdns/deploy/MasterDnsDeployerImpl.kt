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
        PersistentLoggers.debug(TAG, "deploy: start")
        if (!connectAndAuth(credentials, "deploy")) return@flow
        try {
            if (!preflightChecks(credentials.host)) return@flow
            if (!installDocker()) return@flow
            if (!postDockerPortChecks(credentials.host)) return@flow
            if (!buildAndRun(credentials.host)) return@flow
            val key = extractKey() ?: return@flow
            PersistentLoggers.debug(TAG, "deploy: done key_len=${key.length}")
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
        PersistentLoggers.debug(TAG, "removeAmneziaDnsAndContinue: start")
        if (!connectAndAuth(credentials, "removeAmneziaDnsAndContinue")) return@flow
        try {
            emit(MasterDnsDeployState.Removing)
            val removeResult = transport.exec(MasterDnsDockerScripts.removeAmneziaDnsOnly)
            PersistentLoggers.debug(TAG, "removeAmneziaDnsAndContinue: remove result received")
            if (!removeResult.contains(MasterDnsDockerScripts.MARKER_AMNEZIA_DNS_REMOVED) &&
                !removeResult.contains(MasterDnsDockerScripts.MARKER_AMNEZIA_DNS_NOT_FOUND)
            ) {
                emit(MasterDnsDeployState.Error("amnezia_dns_remove_failed"))
                return@flow
            }
            if (!preflightChecks(credentials.host)) return@flow
            if (!installDocker()) return@flow
            if (!postDockerPortChecks(credentials.host)) return@flow
            if (!buildAndRun(credentials.host)) return@flow
            val key = extractKey() ?: return@flow
            PersistentLoggers.debug(
                TAG,
                "removeAmneziaDnsAndContinue: done key_len=${key.length}",
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

    private suspend fun FlowCollector<MasterDnsDeployState>.preflightChecks(serverHost: String): Boolean {
        PersistentLoggers.debug(TAG, "deploy: sudo check")
        val sudoResult = transport.exec(MasterDnsDockerScripts.checkSudoNoPassword)
        PersistentLoggers.debug(TAG, "deploy: sudo result received")
        val sudoError = mapSudoResult(sudoResult)
        if (sudoError != null) {
            emit(MasterDnsDeployState.Error(sudoError))
            return false
        }
        emit(MasterDnsDeployState.CheckingPreflight)
        if (!checkAmneziaDns53Conflict("deploy: amnezia-dns port 53 check")) return false
        if (!checkPort53Availability(serverHost, "deploy: port 53 check")) return false
        return checkResources()
    }

    private suspend fun cleanupLegacyMasterDns() {
        PersistentLoggers.debug(TAG, "deploy: cleanup legacy MasterDNS artifacts")
        val result = transport.exec(MasterDnsDockerScripts.cleanupLegacyMasterDns)
        PersistentLoggers.debug(TAG, "deploy: legacy cleanup result received")
        if (!result.contains(MasterDnsDockerScripts.MARKER_LEGACY_MASTERDNS_CLEANUP_OK)) {
            PersistentLoggers.warn(TAG, "deploy: legacy cleanup returned unexpected result")
        }
    }

    private suspend fun FlowCollector<MasterDnsDeployState>.postDockerPortChecks(serverHost: String): Boolean {
        if (!checkAmneziaDns53Conflict("deploy: post-docker amnezia-dns port 53 check")) return false
        return checkPort53Availability(serverHost, "deploy: post-docker port 53 check")
    }

    private suspend fun FlowCollector<MasterDnsDeployState>.checkAmneziaDns53Conflict(logLabel: String): Boolean {
        PersistentLoggers.debug(TAG, logLabel)
        val amneziaResult = transport.exec(MasterDnsDockerScripts.checkAmneziaDns53)
        PersistentLoggers.debug(TAG, "deploy: amnezia-dns result received")
        parseAmneziaDnsConflict(amneziaResult)?.let { conflict ->
            emit(conflict)
            return false
        }
        return true
    }

    private suspend fun FlowCollector<MasterDnsDeployState>.checkPort53Availability(
        serverHost: String,
        logLabel: String,
    ): Boolean {
        PersistentLoggers.debug(TAG, logLabel)
        val portResult = transport.exec(MasterDnsDockerScripts.checkPort53(serverHost))
        var finalPortResult = portResult
        PersistentLoggers.debug(TAG, "deploy: port result received")
        val initialPortBusy = parsePortBusy(finalPortResult)
        if (initialPortBusy?.owner.isLegacyMasterDnsOwner()) {
            cleanupLegacyMasterDns()
            finalPortResult = transport.exec(MasterDnsDockerScripts.checkPort53(serverHost))
            PersistentLoggers.debug(TAG, "deploy: port after legacy cleanup result received")
        }
        val portBusy = parsePortBusy(finalPortResult)
        if (portBusy != null) {
            emit(portBusy)
            return false
        }
        val portError = mapPortResult(finalPortResult)
        if (portError != null) {
            emit(MasterDnsDeployState.Error(portError))
            return false
        }
        return true
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
        PersistentLoggers.debug(TAG, "deploy: docker install result received")
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

    private suspend fun FlowCollector<MasterDnsDeployState>.buildAndRun(serverHost: String): Boolean {
        emit(MasterDnsDeployState.BuildingImage)
        PersistentLoggers.debug(TAG, "deploy: docker build masterdns-ozero (timeout 300s)")
        val buildResult = transport.exec(MasterDnsDockerScripts.deployMasterDns, timeoutMs = 300_000L)
        PersistentLoggers.debug(TAG, "deploy: build result received")
        if (!buildResult.contains(MasterDnsDockerScripts.MARKER_BUILD_OK)) {
            emit(MasterDnsDeployState.Error(mapBuildError(buildResult)))
            return false
        }
        emit(MasterDnsDeployState.StartingContainer)
        PersistentLoggers.debug(TAG, "deploy: docker run masterdns-ozero published on external host ip udp/53")
        val runResult = transport.exec(MasterDnsDockerScripts.runContainer(serverHost))
        PersistentLoggers.debug(TAG, "deploy: run result received")
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
        PersistentLoggers.debug(TAG, "deploy: firewall result received")
        if (!fwResult.contains("FW_") || fwResult.contains(MasterDnsDockerScripts.MARKER_FW_NONE_OK)) {
            PersistentLoggers.warn(
                TAG,
                "deploy: firewall step не открыл 53/udp — продолжаем, " +
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

    private companion object {
        const val TAG = "MasterDnsDeployer"
        const val CONTAINER_STARTUP_DELAY_MS = 3_000L
    }
}

private const val PORT_BUSY_PREFIX = "PORT_BUSY|"

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

private fun String?.isLegacyMasterDnsOwner(): Boolean {
    if (this == null) return false
    return contains("masterdns", ignoreCase = true) ||
        contains("masterdnsvpn", ignoreCase = true) ||
        contains("MasterDnsVPN", ignoreCase = true)
}

private fun buildClientToml(serverIp: String, encryptionKey: String): String =
    """
    LISTEN_IP = "127.0.0.1"
    LISTEN_PORT = 1080
    PROTOCOL_TYPE = "SOCKS5"
    DATA_ENCRYPTION_METHOD = 5
    SOCKS5_AUTH = false
    LOCAL_DNS_ENABLED = false
    RESOLVER_BALANCING_STRATEGY = 3
    PACKET_DUPLICATION_COUNT = 3
    SETUP_PACKET_DUPLICATION_COUNT = 4
    STREAM_RESOLVER_FAILOVER_RESEND_THRESHOLD = 2
    STREAM_RESOLVER_FAILOVER_COOLDOWN = 2.5
    RECHECK_INACTIVE_SERVERS_ENABLED = true
    AUTO_DISABLE_TIMEOUT_SERVERS = true
    AUTO_DISABLE_TIMEOUT_WINDOW_SECONDS = 30.0
    BASE_ENCODE_DATA = false
    UPLOAD_COMPRESSION_TYPE = 0
    DOWNLOAD_COMPRESSION_TYPE = 0
    COMPRESSION_MIN_SIZE = 120
    MIN_UPLOAD_MTU = 38
    MIN_DOWNLOAD_MTU = 200
    MAX_UPLOAD_MTU = 150
    MAX_DOWNLOAD_MTU = 4000
    AUTO_REMOVE_LOW_MTU_SERVERS = true
    MTU_TEST_RETRIES = 2
    MTU_TEST_TIMEOUT = 2.0
    MTU_TEST_PARALLELISM = 32
    RX_TX_WORKERS = 4
    TUNNEL_PROCESS_WORKERS = 6
    TUNNEL_PACKET_TIMEOUT_SECONDS = 10.0
    DISPATCHER_IDLE_POLL_INTERVAL_SECONDS = 0.020
    RX_CHANNEL_SIZE = 4096
    SOCKS_UDP_ASSOCIATE_READ_TIMEOUT_SECONDS = 30.0
    CLIENT_TERMINAL_STREAM_RETENTION_SECONDS = 45.0
    CLIENT_CANCELLED_SETUP_RETENTION_SECONDS = 120.0
    SESSION_INIT_RETRY_BASE_SECONDS = 1.0
    SESSION_INIT_RETRY_STEP_SECONDS = 1.0
    SESSION_INIT_RETRY_LINEAR_AFTER = 5
    SESSION_INIT_RETRY_MAX_SECONDS = 60.0
    SESSION_INIT_BUSY_RETRY_INTERVAL_SECONDS = 60.0
    SESSION_INIT_RACING_COUNT = 3
    PING_AGGRESSIVE_INTERVAL_SECONDS = 0.100
    PING_LAZY_INTERVAL_SECONDS = 0.750
    PING_COOLDOWN_INTERVAL_SECONDS = 2.0
    PING_COLD_INTERVAL_SECONDS = 15.0
    PING_WARM_THRESHOLD_SECONDS = 8.0
    PING_COOL_THRESHOLD_SECONDS = 20.0
    PING_COLD_THRESHOLD_SECONDS = 30.0
    MAX_PACKETS_PER_BATCH = 8
    ARQ_WINDOW_SIZE = 1000
    ARQ_INITIAL_RTO_SECONDS = 0.5
    ARQ_MAX_RTO_SECONDS = 3.0
    ARQ_CONTROL_INITIAL_RTO_SECONDS = 0.5
    ARQ_CONTROL_MAX_RTO_SECONDS = 2.0
    ARQ_MAX_CONTROL_RETRIES = 126
    ARQ_INACTIVITY_TIMEOUT_SECONDS = 1800.0
    ARQ_DATA_PACKET_TTL_SECONDS = 2400.0
    ARQ_CONTROL_PACKET_TTL_SECONDS = 1200.0
    ARQ_MAX_DATA_RETRIES = 126
    ARQ_DATA_NACK_MAX_GAP = 32
    ARQ_DATA_NACK_INITIAL_DELAY_SECONDS = 0.1
    ARQ_DATA_NACK_REPEAT_SECONDS = 0.8
    ARQ_TERMINAL_DRAIN_TIMEOUT_SECONDS = 120.0
    ARQ_TERMINAL_ACK_WAIT_TIMEOUT_SECONDS = 90.0
    LOG_LEVEL = "INFO"
    ENCRYPTION_KEY = "$encryptionKey"
    SERVER = "$serverIp"
    DOMAINS = ["${MasterDnsDockerScripts.DEFAULT_DOMAIN}"]
    """.trimIndent()

private fun String.sanitizeDeployDiagnostic(maxLen: Int = 700): String =
    replace('\n', ' ')
        .replace('\r', ' ')
        .replace(Regex("""password=[^| ]+"""), "password=<redacted>")
        .replace(Regex("""token=[^| ]+"""), "token=<redacted>")
        .take(maxLen)
