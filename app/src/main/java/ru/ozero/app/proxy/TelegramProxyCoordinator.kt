package ru.ozero.app.proxy

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import ru.ozero.commonvpn.TunnelController
import ru.ozero.commonvpn.TunnelState
import ru.ozero.enginetelegram.TelegramConfigStore
import ru.ozero.enginetelegram.TelegramProxyConfig
import ru.ozero.enginetelegram.TelegramProxyService
import ru.ozero.enginetelegram.TelegramProxyState
import ru.ozero.enginescore.Upstream
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class TelegramProxyCoordinator(
    private val proxyService: TelegramProxyService,
    private val tunnelController: TunnelController,
    private val configStore: TelegramConfigStore,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val maxRestarts: Int = MAX_RESTARTS,
) {

    private val jobRef = AtomicReference<Job?>(null)
    private val restartCount = AtomicInteger(0)

    fun start() {
        val newJob = combine(
            tunnelController.state,
            configStore.config(),
            proxyService.state,
        ) { tunnel, config, proxy -> Triple(tunnel, config, proxy) }
            .distinctUntilChanged()
            .onEach { (tunnelState, config, proxyState) ->
                handle(tunnelState, config, proxyState)
            }
            .launchIn(scope)
        jobRef.getAndSet(newJob)?.cancel()
    }

    private fun handle(
        tunnelState: TunnelState,
        config: TelegramProxyConfig,
        proxyState: TelegramProxyState,
    ) {
        val shouldRun = config.enabled &&
            config.secret.isNotBlank() &&
            tunnelState is TunnelState.Connected
        if (!shouldRun) {
            proxyService.stop()
            restartCount.set(0)
            return
        }
        val connected = tunnelState as TunnelState.Connected
        val upstream = upstreamFor(connected)
        when (proxyState) {
            is TelegramProxyState.Idle -> {
                restartCount.set(0)
                proxyService.start(config, upstream)
            }
            is TelegramProxyState.Error -> {
                if (restartCount.getAndIncrement() < maxRestarts) {
                    proxyService.start(config, upstream)
                }
            }
            is TelegramProxyState.Running -> {
                restartCount.set(0)
            }
            is TelegramProxyState.Starting -> Unit
        }
    }

    private fun upstreamFor(tunnel: TunnelState.Connected): Upstream =
        if (tunnel.socksPort > 0) {
            Upstream.Socks5("127.0.0.1", tunnel.socksPort)
        } else {
            Upstream.None
        }

    fun stop() {
        jobRef.getAndSet(null)?.cancel()
        proxyService.stop()
    }

    private companion object {
        const val MAX_RESTARTS = 3
    }
}
