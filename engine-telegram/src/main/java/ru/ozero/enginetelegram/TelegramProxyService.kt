package ru.ozero.enginetelegram

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CancellationException
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
import kotlinx.coroutines.withContext
import ru.ozero.enginescore.Upstream
import java.util.concurrent.atomic.AtomicReference

class TelegramProxyService(
    private val context: Context,
    private val configStore: TelegramConfigStore,
) {
    private val wrapper by lazy { MtgWrapper(context.applicationInfo.nativeLibraryDir) }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val processRef = AtomicReference<Process?>(null)
    private val jobRef = AtomicReference<Job?>(null)
    private val runMutex = Mutex()

    private val _state = MutableStateFlow<TelegramProxyState>(TelegramProxyState.Idle)
    val state: StateFlow<TelegramProxyState> = _state.asStateFlow()

    fun start(config: TelegramProxyConfig, upstream: Upstream) {
        if (!config.enabled || config.secret.isBlank()) {
            _state.value = TelegramProxyState.Idle
            return
        }
        jobRef.getAndSet(
            scope.launch { runMutex.withLock { runProxy(config, upstream) } },
        )?.cancel()
    }

    fun stop() {
        jobRef.getAndSet(null)?.cancel()
        killProcess()
        _state.value = TelegramProxyState.Idle
    }

    suspend fun generateAndSaveSecret(domain: String): String? {
        val secret = wrapper.generateSecret(domain) ?: return null
        configStore.setSecret(secret)
        return secret
    }

    private suspend fun runProxy(config: TelegramProxyConfig, upstream: Upstream) {
        _state.value = TelegramProxyState.Starting
        val upstreamUrl = (upstream as? Upstream.Socks5)
            ?.let { "socks5://${it.host}:${it.port}" }
        try {
            val process = withContext(Dispatchers.IO) {
                val newProcess = wrapper.startProxy(config.port, config.secret, upstream = upstreamUrl)
                processRef.getAndSet(newProcess)?.let { stale ->
                    runCatching { stale.destroyForcibly() }
                }
                newProcess
            }
            delay(STARTUP_CHECK_MS)
            if (!process.isAlive) {
                val output = withContext(Dispatchers.IO) { process.inputStream.bufferedReader().use { it.readText() } }
                Log.e(TAG, "mtg exited early: $output")
                _state.value = TelegramProxyState.Error("mtg exited: $output")
                return
            }
            _state.value = TelegramProxyState.Running(config.port, config.secret)
            Log.i(TAG, "mtg running port=${config.port}")
            scope.launch(Dispatchers.IO) {
                runCatching { process.inputStream.bufferedReader().use { r -> r.forEachLine { Log.d(TAG, it) } } }
            }
            withContext(Dispatchers.IO) { process.waitFor() }
            if (_state.value is TelegramProxyState.Running) {
                _state.value = TelegramProxyState.Error("mtg exited unexpectedly")
                Log.e(TAG, "mtg process exited unexpectedly")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Log.e(TAG, "mtg launch error: ${e.message}")
            _state.value = TelegramProxyState.Error(e.message ?: "unknown error")
        } finally {
            killProcess()
        }
    }

    private fun killProcess() {
        processRef.getAndSet(null)?.let { process ->
            runCatching { process.destroy() }
            runCatching { process.destroyForcibly() }
        }
    }

    private companion object {
        const val TAG = "TelegramProxyService"
        const val STARTUP_CHECK_MS = 500L
    }
}
