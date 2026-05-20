package ru.ozero.app.ui.settings.engines

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ru.ozero.enginemasterdns.MasterDnsConfigStore
import ru.ozero.enginemasterdns.MasterDnsPersistedConfig

class MasterDnsSettingsViewModelTest {

    @BeforeEach
    fun setUp() {
        kotlinx.coroutines.Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        kotlinx.coroutines.Dispatchers.resetMain()
    }

    @Test
    fun `state reflects store config`() = runTest {
        val store = FakeStore(
            MasterDnsPersistedConfig(enabled = true, configToml = "x", resolvers = listOf("8.8.8.8")),
        )
        val vm = MasterDnsSettingsViewModel(store)
        val state = vm.state.first { it.enabled }
        assertTrue(state.enabled)
        assertEquals("x", state.configToml)
        assertEquals("8.8.8.8", state.resolversText)
    }

    @Test
    fun `resolvers serialized as newline-joined`() = runTest {
        val store = FakeStore(
            MasterDnsPersistedConfig(resolvers = listOf("8.8.8.8", "1.1.1.1")),
        )
        val vm = MasterDnsSettingsViewModel(store)
        val state = vm.state.first { it.resolversText.isNotEmpty() }
        assertEquals("8.8.8.8\n1.1.1.1", state.resolversText)
    }

    @Test
    fun `onResolversChange splits by newline and trims`() = runTest {
        val store = FakeStore(MasterDnsPersistedConfig())
        val vm = MasterDnsSettingsViewModel(store)
        vm.onResolversChange("8.8.8.8\n 1.1.1.1 \n\n")
        assertEquals(listOf("8.8.8.8", "1.1.1.1"), store.resolvers)
    }

    @Test
    fun `onResolversChange with empty input clears`() = runTest {
        val store = FakeStore(MasterDnsPersistedConfig(resolvers = listOf("8.8.8.8")))
        val vm = MasterDnsSettingsViewModel(store)
        vm.onResolversChange("   \n  \n")
        assertTrue(store.resolvers.isEmpty())
    }

    @Test
    fun `onConfigTomlChange persists`() = runTest {
        val store = FakeStore(MasterDnsPersistedConfig())
        val vm = MasterDnsSettingsViewModel(store)
        vm.onConfigTomlChange("DOMAINS=[]")
        assertEquals("DOMAINS=[]", store.toml)
    }

    @Test
    fun `onEnabledChange persists true`() = runTest {
        val store = FakeStore(MasterDnsPersistedConfig())
        val vm = MasterDnsSettingsViewModel(store)
        vm.onEnabledChange(true)
        assertTrue(store.enabled)
    }

    @Test
    fun `onEnabledChange persists false`() = runTest {
        val store = FakeStore(MasterDnsPersistedConfig(enabled = true))
        val vm = MasterDnsSettingsViewModel(store)
        vm.onEnabledChange(false)
        assertFalse(store.enabled)
    }

    private class FakeStore(initial: MasterDnsPersistedConfig) : MasterDnsConfigStore {
        private val flow = MutableStateFlow(initial)
        var enabled: Boolean = initial.enabled
        var toml: String = initial.configToml
        var resolvers: List<String> = initial.resolvers
        override fun config() = flow
        override suspend fun setEnabled(enabled: Boolean) {
            this.enabled = enabled
            flow.value = flow.value.copy(enabled = enabled)
        }
        override suspend fun setConfigToml(toml: String) {
            this.toml = toml
            flow.value = flow.value.copy(configToml = toml)
        }
        override suspend fun setResolvers(resolvers: List<String>) {
            this.resolvers = resolvers
            flow.value = flow.value.copy(resolvers = resolvers)
        }
    }
}
