package ru.ozero.enginemasterdns

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import ru.ozero.enginescore.PersistentLoggers
import java.io.File
import java.util.concurrent.atomic.AtomicReference

interface MasterDnsClientServiceContract {
    val state: StateFlow<MasterDnsClientState>
    fun start(runtime: MasterDnsRuntimeConfig)
    fun stop()
}

class MasterDnsClientService(
    private val workDirProvider: () -> File,
    private val wrapperFactory: () -> MasterDnsClientWrapperContract,
    private val writer: MasterDnsConfigWriter,
    private val startupCheckMs: Long = STARTUP_CHECK_MS,
    private val readinessProbe: suspend (MasterDnsRuntimeConfig) -> Unit = { runtime ->
        MasterDnsSocksPayloadProbe.probe(
            socksHost = "127.0.0.1",
            socksPort = runtime.socksPort,
            targetHost = runtime.readinessHost,
            targetPort = runtime.readinessPort,
            timeoutMs = runtime.readinessConnectTimeoutMs,
        )
    },
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : MasterDnsClientServiceContract {

    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private val processRef = AtomicReference<Process?>(null)
    private val jobRef = AtomicReference<Job?>(null)
    private val watchdogRef = AtomicReference<Job?>(null)
    private val readerRef = AtomicReference<Job?>(null)
    private val runMutex = Mutex()

    private val _state = MutableStateFlow<MasterDnsClientState>(MasterDnsClientState.Idle)
    override val state: StateFlow<MasterDnsClientState> = _state.asStateFlow()

    override fun start(runtime: MasterDnsRuntimeConfig) {
        cancelChildJobs()
        jobRef.getAndSet(
            scope.launch { runMutex.withLock { runClient(runtime) } },
        )?.cancel()
    }

    override fun stop() {
        jobRef.getAndSet(null)?.cancel()
        cancelChildJobs()
        killProcess()
        _state.value = MasterDnsClientState.Idle
    }

    private fun cancelChildJobs() {
        watchdogRef.getAndSet(null)?.cancel()
        readerRef.getAndSet(null)?.cancel()
    }

    private suspend fun runClient(runtime: MasterDnsRuntimeConfig) {
        _state.value = MasterDnsClientState.Starting
        try {
            ensureWorkDir()
            val files = writer.write(runtime)
            val wrapper = wrapperFactory()
            val process = wrapper.startClient(
                files.configPath,
                files.resolversPath,
                logPath = null,
            )
            processRef.getAndSet(process)?.let { stale ->
                runCatching { stale.destroyForcibly() }
            }
            delay(startupCheckMs)
            if (!process.isAlive) {
                val output = process.inputStream.bufferedReader().use { it.readText() }
                _state.value = MasterDnsClientState.Error("masterdns exited: $output")
                PersistentLoggers.error(TAG, "masterdns exited early: $output")
                return
            }
            awaitReadiness(process, runtime)?.let { failure ->
                _state.value = MasterDnsClientState.Error(failure)
                killProcess()
                return
            }
            _state.value = MasterDnsClientState.Running(runtime.socksPort)
            Log.i(TAG, "masterdns running port=${runtime.socksPort}")
            readerRef.getAndSet(
                scope.launch {
                    runCatching {
                        process.inputStream.bufferedReader().use { r ->
                            var lineCount = 0
                            r.forEachLine { line ->
                                if (lineCount < MAX_LOG_LINES) {
                                    Log.d(TAG, line)
                                    lineCount++
                                    if (lineCount == MAX_LOG_LINES) {
                                        PersistentLoggers.warn(
                                            TAG,
                                            "masterdns stdout truncated after $MAX_LOG_LINES lines",
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
            )?.cancel()
            watchdogRef.getAndSet(
                scope.launch {
                    while (process.isAlive) {
                        delay(LIVENESS_POLL_MS)
                    }
                    if (_state.value is MasterDnsClientState.Running) {
                        _state.value = MasterDnsClientState.Error("masterdns exited unexpectedly")
                        PersistentLoggers.error(TAG, "masterdns process exited unexpectedly")
                    }
                },
            )?.cancel()
        } catch (e: CancellationException) {
            killProcess()
            throw e
        } catch (t: Throwable) {
            PersistentLoggers.error(TAG, "masterdns launch error: ${t.message}", t)
            _state.value = MasterDnsClientState.Error(t.message ?: "unknown error")
            killProcess()
        }
    }

    private fun ensureWorkDir() {
        workDirProvider().mkdirs()
    }

    private suspend fun awaitReadiness(process: Process, runtime: MasterDnsRuntimeConfig): String? {
        var lastFailure: Throwable? = null
        val readiness = withTimeoutOrNull(runtime.readinessTimeoutMs) {
            while (true) {
                if (!process.isAlive) return@withTimeoutOrNull "masterdns exited before payload readiness"
                try {
                    readinessProbe(runtime)
                    return@withTimeoutOrNull READINESS_OK
                } catch (e: CancellationException) {
                    throw e
                } catch (t: Throwable) {
                    lastFailure = t
                    delay(runtime.readinessPollIntervalMs)
                }
            }
        }
        if (readiness == READINESS_OK) return null
        if (readiness != null) return readiness
        val reason = lastFailure?.message ?: "timeout"
        PersistentLoggers.error(TAG, "masterdns payload probe failed: $reason", lastFailure)
        return "masterdns payload probe failed: $reason"
    }

    private fun killProcess() {
        processRef.getAndSet(null)?.let { process ->
            runCatching { process.destroy() }
            runCatching { process.destroyForcibly() }
        }
    }

    private companion object {
        const val TAG = "MasterDnsService"
        const val READINESS_OK = "ok"
        const val STARTUP_CHECK_MS = 500L
        const val LIVENESS_POLL_MS = 500L
        const val MAX_LOG_LINES = 200
    }
}
