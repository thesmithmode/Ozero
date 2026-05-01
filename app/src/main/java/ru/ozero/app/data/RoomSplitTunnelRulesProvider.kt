package ru.ozero.app.data

import kotlinx.coroutines.flow.first
import ru.ozero.commonvpn.SplitTunnelRulesProvider
import ru.ozero.corestorage.dao.AppSplitRuleDao
import javax.inject.Inject

class RoomSplitTunnelRulesProvider @Inject constructor(
    private val dao: AppSplitRuleDao,
) : SplitTunnelRulesProvider {

    override suspend fun activePackages(): Set<String> {
        val rules = runCatching { dao.observeAll().first() }.getOrDefault(emptyList())
        return rules.map { it.packageName }.toSet()
    }
}
