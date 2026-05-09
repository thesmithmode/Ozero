package ru.ozero.commonvpn

interface SplitTunnelRulesProvider {
    suspend fun allowlistPackages(): Set<String>
    suspend fun blocklistPackages(): Set<String>

    object NoOp : SplitTunnelRulesProvider {
        override suspend fun allowlistPackages() = emptySet<String>()
        override suspend fun blocklistPackages() = emptySet<String>()
    }
}
