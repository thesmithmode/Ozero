package ru.ozero.enginemasterdns

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MasterDnsResolversCacheTest {

    @Test
    fun `snapshot reflects current config`() = runTest {
        val flow = MutableStateFlow(MasterDnsPersistedConfig(resolvers = listOf("8.8.8.8")))
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val cache = MasterDnsResolversCache(config = flow, scope = scope)
        assertEquals(listOf("8.8.8.8"), cache.snapshot())
    }

    @Test
    fun `snapshot updates after config emit`() = runTest {
        val flow = MutableStateFlow(MasterDnsPersistedConfig(resolvers = emptyList()))
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val cache = MasterDnsResolversCache(config = flow, scope = scope)
        assertEquals(emptyList<String>(), cache.snapshot())
        flow.value = MasterDnsPersistedConfig(resolvers = listOf("1.1.1.1", "9.9.9.9"))
        assertEquals(listOf("1.1.1.1", "9.9.9.9"), cache.snapshot())
    }

    @Test
    fun `snapshot empty before first emit collected`() = runTest {
        val flow = MutableStateFlow(MasterDnsPersistedConfig(resolvers = listOf("8.8.8.8")))
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val cache = MasterDnsResolversCache(config = flow, scope = scope)
        assertEquals(listOf("8.8.8.8"), cache.snapshot())
    }
}
