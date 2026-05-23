package ru.ozero.enginemasterdns.deploy

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import ru.ozero.enginescore.PersistentLoggers

internal class MasterDnsDeployerImpl(
    private val transport: SshTransport,
) : MasterDnsServerDeployer {

    override fun deploy(credentials: MasterDnsDeployCredentials): Flow<MasterDnsDeployState> = flow {
        emit(MasterDnsDeployState.Connecting)
        val connected = try {
            transport.connect(credentials.host, credentials.port)
            true
        } catch (e: Exception) {
            PersistentLoggers.warn(TAG, "deploy: connection failed", e)
            transport.close()
            emit(MasterDnsDeployState.Error("connection_failed"))
            false
        }
        if (!connected) {
            credentials.clear()
            return@flow
        }
        val authed = try {
            transport.auth(credentials.login, credentials.password)
            true
        } catch (e: Exception) {
            PersistentLoggers.warn(TAG, "deploy: auth failed", e)
            transport.close()
            emit(MasterDnsDeployState.Error("auth_failed"))
            false
        } finally {
            credentials.clear()
        }
        if (!authed) return@flow

        try {
            val sudoResult = transport.exec(MasterDnsDockerScripts.checkSudoNoPassword)
            when {
                sudoResult.contains(MasterDnsDockerScripts.MARKER_ERR_SUDO_NOT_INSTALLED) -> {
                    emit(MasterDnsDeployState.Error("sudo_not_installed")); return@flow
                }
                sudoResult.contains(MasterDnsDockerScripts.MARKER_ERR_SUDO_PWD_REQUIRED) -> {
                    emit(MasterDnsDeployState.Error("sudo_pwd_required")); return@flow
                }
                sudoResult.contains(MasterDnsDockerScripts.MARKER_ERR_SUDO_NOT_ALLOWED) -> {
                    emit(MasterDnsDeployState.Error("sudo_not_allowed")); return@flow
                }
                sudoResult.contains(MasterDnsDockerScripts.MARKER_ERR_SUDO_NO_HOME) -> {
                    emit(MasterDnsDeployState.Error("sudo_no_home")); return@flow
                }
                sudoResult.contains(MasterDnsDockerScripts.MARKER_ERR_SUDO_NOT_IN_GROUP) -> {
                    emit(MasterDnsDeployState.Error("sudo_not_in_group")); return@flow
                }
            }

            emit(MasterDnsDeployState.CheckingPreflight)
            val portResult = transport.exec(MasterDnsDockerScripts.checkPort53)
            if (portResult.contains(MasterDnsDockerScripts.MARKER_PORT_BUSY)) {
                emit(MasterDnsDeployState.Error("port_53_busy"))
                return@flow
            }

            val resources = transport.exec(MasterDnsDockerScripts.checkResources)
            val parts = resources.trim().split(" ")
            val freeMem = parts.getOrNull(0)?.trim()?.toIntOrNull() ?: 0
            val freeDisk = parts.getOrNull(1)?.trim()?.toIntOrNull() ?: 0
            val insufficientResources = freeMem < MasterDnsDockerScripts.MIN_FREE_RAM_MB ||
                freeDisk < MasterDnsDockerScripts.MIN_FREE_DISK_MB
            if (insufficientResources) {
                emit(MasterDnsDeployState.Error("insufficient_resources"))
                return@flow
            }

            emit(MasterDnsDeployState.InstallingDocker)
            val dockerResult = transport.exec(MasterDnsDockerScripts.installDocker, timeoutMs = 360_000L)
            if (dockerResult.contains(MasterDnsDockerScripts.MARKER_ERR_DPKG_LOCKED)) {
                emit(MasterDnsDeployState.Error("dpkg_locked"))
                return@flow
            }
            if (!dockerResult.contains(MasterDnsDockerScripts.MARKER_DOCKER_OK)) {
                emit(MasterDnsDeployState.Error("docker_install_failed"))
                return@flow
            }

            emit(MasterDnsDeployState.BuildingImage)
            val buildResult = transport.exec(MasterDnsDockerScripts.deployMasterDns, timeoutMs = 300_000L)
            if (!buildResult.contains(MasterDnsDockerScripts.MARKER_BUILD_OK)) {
                emit(MasterDnsDeployState.Error("build_failed"))
                return@flow
            }

            emit(MasterDnsDeployState.StartingContainer)
            val runResult = transport.exec(MasterDnsDockerScripts.runContainer)
            if (!runResult.contains(MasterDnsDockerScripts.MARKER_RUN_OK)) {
                emit(MasterDnsDeployState.Error("run_failed"))
                return@flow
            }
            delay(CONTAINER_STARTUP_DELAY_MS)

            emit(MasterDnsDeployState.ExtractingKey)
            val key = transport.exec(MasterDnsDockerScripts.readEncryptKey).trim()
            if (key.isEmpty()) {
                emit(MasterDnsDeployState.Error("key_extraction_failed"))
                return@flow
            }

            val toml = buildClientToml(serverIp = credentials.host, encryptionKey = key)
            emit(MasterDnsDeployState.Done(toml))
        } catch (e: Exception) {
            PersistentLoggers.warn(TAG, "deploy: unexpected error", e)
            emit(MasterDnsDeployState.Error("unexpected_error"))
        } finally {
            transport.close()
        }
    }

    override fun undeploy(credentials: MasterDnsDeployCredentials): Flow<MasterDnsDeployState> = flow {
        emit(MasterDnsDeployState.Connecting)
        val connected = try {
            transport.connect(credentials.host, credentials.port)
            true
        } catch (e: Exception) {
            PersistentLoggers.warn(TAG, "undeploy: connection failed", e)
            transport.close()
            emit(MasterDnsDeployState.Error("connection_failed"))
            false
        }
        if (!connected) {
            credentials.clear()
            return@flow
        }
        val authed = try {
            transport.auth(credentials.login, credentials.password)
            true
        } catch (e: Exception) {
            PersistentLoggers.warn(TAG, "undeploy: auth failed", e)
            transport.close()
            emit(MasterDnsDeployState.Error("auth_failed"))
            false
        } finally {
            credentials.clear()
        }
        if (!authed) return@flow

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

    private companion object {
        const val TAG = "MasterDnsDeployer"
        const val CONTAINER_STARTUP_DELAY_MS = 3_000L
    }
}
