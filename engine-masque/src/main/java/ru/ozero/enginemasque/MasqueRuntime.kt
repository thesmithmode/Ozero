package ru.ozero.enginemasque

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.IOException
import java.nio.file.Files
import kotlin.coroutines.coroutineContext

class MasqueRuntime internal constructor(
    private val binary: File,
    private val privateDirectory: File,
    private val launcher: MasqueProcessLauncher,
    private val readiness: MasqueReadinessProbe,
    private val startupTimeoutMs: Long = 6_000,
) {
    constructor(binary: File, privateDirectory: File) : this(
        binary,
        privateDirectory,
        NativeMasqueProcessLauncher,
        TlsMasqueReadinessProbe,
    )

    private val mutex = Mutex()
    private var session: Session? = null

    suspend fun register(acceptedTerms: Boolean): MasqueProfile = mutex.withLock {
        require(acceptedTerms) { "Cloudflare terms must be accepted" }
        withContext(Dispatchers.IO) {
            check(session == null) { "Stop MASQUE before registration" }
            val workspace = newWorkspace()
            val config = File(workspace, CONFIG_NAME)
            try {
                val process = launcher.launch(MasqueCommand.register(binary, config))
                try {
                    readRegistration(process, config)
                } finally {
                    process.close()
                }
            } finally {
                workspace.deleteRecursively()
            }
        }
    }

    private suspend fun readRegistration(process: MasqueProcess, config: File): MasqueProfile {
        val code = withTimeoutOrNull(45_000) {
            while (process.alive) delay(100)
            process.exitCode()
        }
        check(code == 0 && config.isFile && config.length() in 1..MasqueProfile.MAX_BYTES.toLong()) {
            "MASQUE registration failed"
        }
        return MasqueProfile.parse(config.readText())
    }

    suspend fun start(
        profile: MasqueProfile,
        port: Int,
        transport: MasqueTransport,
        ipv6Enabled: Boolean = false,
    ): MasqueTransport? =
        mutex.withLock {
            require(port in 1..65535) { "Invalid SOCKS port" }
            try {
                withContext(Dispatchers.IO) {
                    stopLocked()
                    startLocked(profile, port, transport, ipv6Enabled)
                }
            } catch (e: CancellationException) {
                withContext(NonCancellable + Dispatchers.IO) { stopLocked() }
                throw e
            }
        }

    private suspend fun startLocked(
        profile: MasqueProfile,
        port: Int,
        transport: MasqueTransport,
        ipv6Enabled: Boolean,
    ): MasqueTransport? {
        for (attempt in transport.attempts()) {
            val started = startAttempt(profile, port, attempt, ipv6Enabled, endpointIpv6 = false)
                ?: if (profile.hasIpv6Endpoint(attempt)) {
                    startAttempt(profile, port, attempt, ipv6Enabled, endpointIpv6 = true)
                } else {
                    null
                }
            if (started != null) {
                session = started
                return attempt
            }
        }
        return null
    }

    private suspend fun startAttempt(
        profile: MasqueProfile,
        port: Int,
        transport: MasqueTransport,
        ipv6Enabled: Boolean,
        endpointIpv6: Boolean,
    ): Session? {
        val workspace = newWorkspace()
        val config = File(workspace, CONFIG_NAME)
        var child: MasqueProcess? = null
        var started = false
        try {
            config.writeText(profile.json)
            val command = MasqueCommand.socks(binary, config, port, transport, ipv6Enabled, endpointIpv6)
            val process = launcher.launch(command)
            child = process
            started = awaitReady(process, workspace, port)
            return if (started) Session(process, workspace, port) else null
        } catch (_: IOException) {
            return null
        } finally {
            if (!started) {
                try {
                    child?.close()
                } finally {
                    workspace.deleteRecursively()
                }
            }
        }
    }

    private suspend fun awaitReady(process: MasqueProcess, workspace: File, port: Int): Boolean =
        withTimeoutOrNull(startupTimeoutMs) {
            val marker = File(workspace, READY_NAME)
            while (process.alive) {
                if (marker.isFile && readiness.check(port) && process.alive) {
                    coroutineContext.ensureActive()
                    return@withTimeoutOrNull true
                }
                delay(100)
            }
            false
        } == true

    suspend fun isReady(): Boolean = mutex.withLock {
        withContext(Dispatchers.IO) {
            val active = session
            active != null && active.process.alive && readiness.check(active.port) && active.process.alive
        }
    }

    suspend fun stop() = mutex.withLock {
        withContext(NonCancellable + Dispatchers.IO) { stopLocked() }
    }

    private fun stopLocked() {
        val active = session ?: return
        active.process.close()
        session = null
        active.workspace.deleteRecursively()
    }

    private fun newWorkspace(): File {
        check(binary.isFile && binary.canExecute()) { "MASQUE binary unavailable" }
        check(privateDirectory.isDirectory) { "MASQUE private directory unavailable" }
        return Files.createTempDirectory(privateDirectory.toPath(), "masque-").toFile()
    }

    private data class Session(val process: MasqueProcess, val workspace: File, val port: Int)

    private companion object {
        const val CONFIG_NAME = "config.json"
        const val READY_NAME = "ready"
    }
}
