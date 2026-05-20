package ru.ozero.enginemasterdns

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class MasterDnsResolversCache(
    config: Flow<MasterDnsPersistedConfig>,
    scope: CoroutineScope,
) {

    private val resolvers = config
        .map { it.resolvers }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    fun snapshot(): List<String> = resolvers.value
}
