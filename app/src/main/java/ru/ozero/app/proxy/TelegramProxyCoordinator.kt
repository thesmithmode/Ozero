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
import ru.ozero.enginetelegram.TelegramProxyService
import ru.ozero.enginescore.Upstream
import java.util.concurrent.atomic.AtomicReference

class TelegramProxyCoordinator(
    private val proxyService: TelegramProxyService,
    private val tunnelController: TunnelController,
    private val configStore: TelegramConfigStore,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {

    private val jobRef = AtomicReference<Job?>(null)

    fun start() {
        val newJob = combine(
            tunnelController.state,
            configStore.config(),
        ) { tunnelState, config ->
            tunnelState to config
        }
            .distinctUntilChanged()
            .onEach { (tunnelState, config) ->
                if (!config.enabled || config.secret.isBlank()) {
                    proxyService.stop()
                    return@onEach
                }
                if (tunnelState !is TunnelState.Connected) {
                    proxyService.stop()
                    return@onEach
                }
                val upstream = if (tunnelState.socksPort > 0) {
                    Upstream.Socks5("127.0.0.1", tunnelState.socksPort)
                } else {
                    Upstream.None
                }
                proxyService.start(config, upstream)
            }
            .launchIn(scope)
        jobRef.getAndSet(newJob)?.cancel()
    }

    fun stop() {
        jobRef.getAndSet(null)?.cancel()
        proxyService.stop()
    }
}
