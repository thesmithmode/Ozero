package ru.ozero.enginetor

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import ru.ozero.coreapi.Engine
import ru.ozero.coreapi.EngineCapabilities
import ru.ozero.coreapi.EngineConfig
import ru.ozero.coreapi.EngineId
import ru.ozero.coreapi.EngineStats
import ru.ozero.coreapi.ProbeResult
import ru.ozero.coreapi.StartResult
import ru.ozero.coreorchestrator.probe.Socks5HandshakeProbe
import ru.ozero.enginetor.bridges.TorBridge
import ru.ozero.enginetor.config.TorBuildOptions
import ru.ozero.enginetor.config.TorConfigBuilder
import ru.ozero.enginetor.dynamicmod.DynamicTorInstaller
import ru.ozero.enginetor.dynamicmod.InstallResult

/**
 * Tor engine с PT-bridges (obfs4 / snowflake / webtunnel / conjure / meek_lite).
 *
 * Особенности:
 * - Подгружает on-demand модуль `:dynamic_tor` через [installer] перед стартом.
 * - Собирает torrc динамически из bridges + ptBinaries.
 * - probe = TCP-connect к локальному SocksPort + проверка [delegate.isBootstrapped].
 */
class TorEngine(
    private val delegate: LibTorDelegate,
    private val installer: DynamicTorInstaller,
    private val configBuilder: TorConfigBuilder = TorConfigBuilder(),
    private val bridges: List<TorBridge> = emptyList(),
    private val buildOptions: TorBuildOptions = TorBuildOptions(),
) : Engine {

    override val id = EngineId.TOR

    override val capabilities = EngineCapabilities(
        supportsTcp = true,
        supportsUdp = false,
        supportsDoH = false,
        localOnly = false,
        requiresServer = false, // bridges опциональны если direct TCP работает
    )

    @Volatile private var started: Boolean = false
    private val _stats = MutableStateFlow(EngineStats())

    override suspend fun start(config: EngineConfig): StartResult {
        require(config is EngineConfig.Tor) { "TorEngine требует EngineConfig.Tor" }

        // Шаг 1: убедиться что dynamic module установлен (~50 МБ скачивается по запросу).
        when (val installResult = installer.ensureInstalled()) {
            is InstallResult.Installed, InstallResult.AlreadyInstalled -> Unit
            is InstallResult.Failed -> {
                Log.e(TAG, "dynamic_tor install failed: ${installResult.reason}")
                return StartResult.Failure(reason = "dynamic_tor: ${installResult.reason}")
            }
            is InstallResult.Installing -> {
                Log.e(TAG, "dynamic_tor ещё устанавливается (${installResult.percent}%)")
                return StartResult.Failure(reason = "dynamic_tor устанавливается, повторите позже")
            }
        }

        // Шаг 2: собрать torrc.
        val torrc = runCatching {
            configBuilder.build(
                bridges = bridges,
                options = buildOptions.copy(socksPort = config.socksPort),
            )
        }.getOrElse {
            return StartResult.Failure(reason = "torrc build: ${it.message}", cause = it)
        }

        Log.i(TAG, "start socksPort=${config.socksPort} bridges=${bridges.size}")
        return withContext(Dispatchers.IO) {
            try {
                val code = delegate.startTor(torrc)
                if (code == 0) {
                    started = true
                    StartResult.Success(socksPort = config.socksPort)
                } else {
                    Log.e(TAG, "startTor код $code")
                    StartResult.Failure(reason = "startTor вернул код $code")
                }
            } catch (e: Throwable) {
                Log.e(TAG, "startTor исключение", e)
                StartResult.Failure(reason = e.message ?: "spawn error", cause = e)
            }
        }
    }

    override suspend fun stop() {
        Log.i(TAG, "stop")
        withContext(Dispatchers.IO) {
            runCatching { delegate.stopTor() }
                .onFailure { Log.w(TAG, "stopTor исключение: ${it.message}") }
            started = false
        }
    }

    override suspend fun probe(): ProbeResult {
        if (!started) {
            return ProbeResult.Failure(reason = "движок не запущен")
        }
        if (!delegate.isBootstrapped()) {
            return ProbeResult.Failure(reason = "tor не bootstrapped (${delegate.bootstrapPercent()}%)")
        }
        return try {
            val latency = Socks5HandshakeProbe.probe("127.0.0.1", buildOptions.socksPort, timeoutMs = 5_000)
            ProbeResult.Success(latencyMs = latency)
        } catch (e: Exception) {
            ProbeResult.Failure(reason = e.message ?: "connection refused")
        }
    }

    override fun stats(): Flow<EngineStats> = _stats.asStateFlow()

    private companion object {
        const val TAG = "TorEngine"
    }
}
