package ru.ozero.app.ui

import ru.ozero.commonnet.IpInfo
import ru.ozero.commonnet.IpInfoProvider
import ru.ozero.enginescore.ExitNodeStrategy

class ExitNodeResolver(
    private val ipInfoProvider: IpInfoProvider,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {

    suspend fun resolve(strategy: ExitNodeStrategy): IpInfoState = when (strategy) {
        ExitNodeStrategy.DirectHttp -> ipInfoProvider.fetch().toState()
        is ExitNodeStrategy.ViaSocks -> ipInfoProvider.fetchVia(strategy.host, strategy.port).toState()
        is ExitNodeStrategy.LocationOnly -> IpInfoState.Loaded(
            IpInfo(
                ip = "",
                country = strategy.country,
                countryCode = strategy.countryCode,
                city = null,
                fetchedAtMs = clock(),
            ),
        )
        is ExitNodeStrategy.ProviderLabel -> IpInfoState.Loaded(
            IpInfo(
                ip = "",
                country = strategy.label,
                countryCode = null,
                city = null,
                fetchedAtMs = clock(),
            ),
        )
        is ExitNodeStrategy.AutoSelected -> IpInfoState.AutoSelected
        is ExitNodeStrategy.Unavailable -> IpInfoState.Error(strategy.reason)
    }

    private fun Result<IpInfo>.toState(): IpInfoState = fold(
        onSuccess = { IpInfoState.Loaded(it) },
        onFailure = {
            if (it is kotlinx.coroutines.CancellationException) throw it
            IpInfoState.Error(it.message ?: it.javaClass.simpleName)
        },
    )
}
