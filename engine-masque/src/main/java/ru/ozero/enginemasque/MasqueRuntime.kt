package ru.ozero.enginemasque

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.IOException

class MasqueRuntime internal constructor(
    private val binary: File,
    private val privateDirectory: File,
    private val launcher: MasqueProcessLauncher,
    private val readiness: MasqueReadinessProbe,
    private val startupTimeoutMs: Long = 15_000,
) {
    constructor(binary: File, privateDirectory: File) : this(
        binary,
        privateDirectory,
        NativeMasqueProcessLauncher,
        TlsMasqueReadinessProbe,
    )

    private val mutex = Mutex()
    private var running: MasqueProcess? = null
    private var activeConfig: File? = null
    private var activePort: Int? = null

    suspend fun register(acceptedTerms: Boolean): MasqueProfile = withContext(Dispatchers.IO) {
        require(acceptedTerms) { "Cloudflare terms must be accepted" }
        mutex.withLock {
            check(running == null) { "Stop MASQUE before registration" }
            val workspace = newWorkspace()
            val config = File(workspace, "config.json")
            try {
                val process = launcher.launch(MasqueCommand.register(binary, config))
                try {
                    val code = withTimeoutOrNull(45_000) {
                        while (process.alive) delay(100)
                        process.exitCode()
                    }
                    check(code == 0 && config.isFile && config.length() in 1..MasqueProfile.MAX_BYTES.toLong()) {
                        "MASQUE registration failed"
                    }
                    MasqueProfile.parse(config.readText())
                } finally {
                    process.close()
                }
            } finally {
                workspace.deleteRecursively()
            }
        }
    }

    suspend fun start(profile: MasqueProfile, port: Int, transport: MasqueTransport): MasqueTransport? =
        withContext(Dispatchers.IO) {
            require(port in 1..65535) { "Invalid SOCKS port" }
            mutex.withLock {
                stopLocked()
                for (attempt in transport.attempts()) {
                    val config = File(newWorkspace(), "config.json")
                    var process: MasqueProcess? = null
                    var keep = false
                    try {
                        config.writeText(profile.json)
                        val child = launcher.launch(MasqueCommand.socks(binary, config, port, attempt))
                        process = child
                        val ready = withTimeoutOrNull(startupTimeoutMs) {
                            while (child.alive) {
                                if (readiness.check(port) && child.alive) return@withTimeoutOrNull true
                                delay(100)
                            }
                            false
                        } == true
                        if (ready) {
                            running = child
                            activeConfig = config
                            activePort = port
                            keep = true
                            return@withLock attempt
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: IOException) {
                        keep = false
                    } finally {
                        if (!keep) {
                            try {
                                process?.close()
                            } finally {
                                config.parentFile?.deleteRecursively()
                            }
                        }
                    }
                }
                null
            }
        }

    suspend fun isReady(): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            val port = activePort
            running?.alive == true && port != null && readiness.check(port)
        }
    }

    suspend fun stop() = withContext(Dispatchers.IO) {
        mutex.withLock { stopLocked() }
    }

    private fun stopLocked() {
        running?.close()
        running = null
        activePort = null
        activeConfig?.parentFile?.deleteRecursively()
        activeConfig = null
    }

    private fun newWorkspace(): File {
        check(binary.isFile && binary.canExecute()) { "MASQUE binary unavailable" }
        check(privateDirectory.isDirectory) { "MASQUE private directory unavailable" }
        return java.nio.file.Files.createTempDirectory(privateDirectory.toPath(), "masque-").toFile()
    }
}
