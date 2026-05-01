package ru.ozero.app.ui.strategy

import ru.ozero.enginebyedpi.strategy.SocksProbeClient

fun interface StrategyProbeClientFactory {
    fun create(socksPort: Int): SocksProbeClient
}
