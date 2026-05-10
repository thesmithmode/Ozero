package ru.ozero.app.data

import kotlinx.coroutines.flow.first
import ru.ozero.commonvpn.SplitTunnelRulesProvider
import ru.ozero.corestorage.dao.AppSplitRuleDao
import javax.inject.Inject

class RoomSplitTunnelRulesProvider @Inject constructor(
    private val dao: AppSplitRuleDao,
) : SplitTunnelRulesProvider {

    override suspend fun allowlistPackages(): Set<String> =
        runCatching { dao.observeAll().first() }
            .getOrDefault(emptyList())
            .filter { !it.isExcluded }
            .map { it.packageName }
            .toSet()

    override suspend fun blocklistPackages(): Set<String> =
        runCatching { dao.observeAll().first() }
            .getOrDefault(emptyList())
            .filter { it.isExcluded }
            .map { it.packageName }
            .toSet()
}
