package ru.ozero.commonvpn

interface SplitTunnelRulesProvider {
    suspend fun activePackages(): Set<String>

    object NoOp : SplitTunnelRulesProvider {
        override suspend fun activePackages(): Set<String> = emptySet()
    }
}
