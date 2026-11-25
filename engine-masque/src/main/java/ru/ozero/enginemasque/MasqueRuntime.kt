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
import ru.ozero.enginescore.PersistentLoggers
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlin.coroutines.coroutineContext

class MasqueRuntime internal constructor(
    private val binary: File,
    private val privateDirectory: File,
    private val launcher: MasqueProcessLauncher,
    private val readiness: MasqueReadinessProbe,
    private val startupTimeoutMs: Long = 6_000,
    private val registrationTimeoutMs: Long = 45_000,
    private val reportFailure: (String) -> Unit = { PersistentLoggers.warn("MasqueRuntime", it) },
) {
    constructor(binary: File, privateDirectory: File) : this(
        binary,
        privateDirectory,
        NativeMasqueProcessLauncher,
        TlsMasqueReadinessProbe(),
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

    private fun registrationExitReason(code: Int): String = when (code) {
        20 -> "Cloudflare registration request failed"
        21 -> "MASQUE key generation failed"
        22 -> "Cloudflare key enrollment failed"
        23 -> "MASQUE could not save the registered profile"
        else -> "registration process exited with code $code"
    }

    private suspend fun readRegistration(process: MasqueProcess, config: File): MasqueProfile {
        val code = withTimeoutOrNull(registrationTimeoutMs) {
            while (process.alive) delay(100)
            process.exitCode()
        }
        val failure = when {
            code == null -> "registration timed out"
            code != 0 -> registrationExitReason(code)
            !config.isFile || config.length() !in 1..MasqueProfile.MAX_BYTES.toLong() ->
                "registration produced no usable profile"
            else -> null
        }
        if (failure != null) {
            reportFailure(failure)
            error(failure)
        }
        return try {
            MasqueProfile.parse(config.readText())
        } catch (e: IllegalArgumentException) {
            reportFailure("registration returned an invalid profile")
            throw e
        }
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
        val attempt = "startup $transport IPv${if (endpointIpv6) 6 else 4}"
        try {
            config.writeText(profile.json)
            val command = MasqueCommand.socks(binary, config, port, transport, ipv6Enabled, endpointIpv6)
            val process = launcher.launch(command)
            child = process
            started = awaitReady(process, workspace, port)
            if (started) return Session(process, workspace, port)
            val reason = if (process.alive) "readiness timed out" else "process exited before readiness"
            reportFailure("$attempt: $reason")
            return null
        } catch (e: IOException) {
            reportFailure("$attempt: ${e.javaClass.simpleName}")
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
        return File(privateDirectory, "masque-${UUID.randomUUID()}").apply {
            check(mkdir()) { "Cannot create MASQUE workspace" }
        }
    }

    private data class Session(val process: MasqueProcess, val workspace: File, val port: Int)

    private companion object {
        const val CONFIG_NAME = "config.json"
        const val READY_NAME = "ready"
    }
}
