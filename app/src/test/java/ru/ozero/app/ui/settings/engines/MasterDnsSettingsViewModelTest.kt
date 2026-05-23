package ru.ozero.app.ui.settings.engines

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ru.ozero.enginemasterdns.MasterDnsConfigStore
import ru.ozero.enginemasterdns.MasterDnsPersistedConfig
import ru.ozero.enginemasterdns.deploy.MasterDnsDeployCredentials
import ru.ozero.enginemasterdns.deploy.MasterDnsDeployState
import ru.ozero.enginemasterdns.deploy.MasterDnsServerDeployer

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
        val vm = MasterDnsSettingsViewModel(store, FakeDeployer(MasterDnsDeployState.Idle))
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
        val vm = MasterDnsSettingsViewModel(store, FakeDeployer(MasterDnsDeployState.Idle))
        val state = vm.state.first { it.resolversText.isNotEmpty() }
        assertEquals("8.8.8.8\n1.1.1.1", state.resolversText)
    }

    @Test
    fun `onResolversChange splits by newline and trims`() = runTest {
        val store = FakeStore(MasterDnsPersistedConfig())
        val vm = MasterDnsSettingsViewModel(store, FakeDeployer(MasterDnsDeployState.Idle))
        vm.onResolversChange("8.8.8.8\n 1.1.1.1 \n\n")
        assertEquals(listOf("8.8.8.8", "1.1.1.1"), store.resolvers)
    }

    @Test
    fun `onResolversChange with empty input clears`() = runTest {
        val store = FakeStore(MasterDnsPersistedConfig(resolvers = listOf("8.8.8.8")))
        val vm = MasterDnsSettingsViewModel(store, FakeDeployer(MasterDnsDeployState.Idle))
        vm.onResolversChange("   \n  \n")
        assertTrue(store.resolvers.isEmpty())
    }

    @Test
    fun `onConfigTomlChange persists`() = runTest {
        val store = FakeStore(MasterDnsPersistedConfig())
        val vm = MasterDnsSettingsViewModel(store, FakeDeployer(MasterDnsDeployState.Idle))
        vm.onConfigTomlChange("DOMAINS=[]")
        assertEquals("DOMAINS=[]", store.toml)
    }

    @Test
    fun `onEnabledChange persists true`() = runTest {
        val store = FakeStore(MasterDnsPersistedConfig())
        val vm = MasterDnsSettingsViewModel(store, FakeDeployer(MasterDnsDeployState.Idle))
        vm.onEnabledChange(true)
        assertTrue(store.enabled)
    }

    @Test
    fun `onEnabledChange persists false`() = runTest {
        val store = FakeStore(MasterDnsPersistedConfig(enabled = true))
        val vm = MasterDnsSettingsViewModel(store, FakeDeployer(MasterDnsDeployState.Idle))
        vm.onEnabledChange(false)
        assertFalse(store.enabled)
    }

    @Test
    fun `state includes serverIp and serverPort from store`() = runTest {
        val store = FakeStore(MasterDnsPersistedConfig(serverIp = "1.2.3.4", serverPort = 2222))
        val vm = MasterDnsSettingsViewModel(store, FakeDeployer(MasterDnsDeployState.Idle))
        val state = vm.state.first { it.serverIp == "1.2.3.4" }
        assertEquals("1.2.3.4", state.serverIp)
        assertEquals(2222, state.serverPort)
    }

    @Test
    fun `deploy success auto-fills configToml and persists serverIp and serverPort`() = runTest {
        val toml = "ENCRYPTION_KEY = \"abc123\"\nPROTOCOL_TYPE = \"SOCKS5\""
        val store = FakeStore(MasterDnsPersistedConfig())
        val vm = MasterDnsSettingsViewModel(
            store,
            FakeDeployer(
                MasterDnsDeployState.Connecting,
                MasterDnsDeployState.CheckingPreflight,
                MasterDnsDeployState.InstallingDocker,
                MasterDnsDeployState.BuildingImage,
                MasterDnsDeployState.StartingContainer,
                MasterDnsDeployState.ExtractingKey,
                MasterDnsDeployState.Done(toml),
            ),
        )
        vm.onDeployClick(host = "10.0.0.1", port = 22, login = "root", password = "pass".toCharArray())
        val state = vm.state.first { it.deployState is MasterDnsDeployState.Done }
        assertEquals(toml, store.toml)
        assertEquals("10.0.0.1", store.serverIp)
        assertEquals(22, store.serverPort)
        assertInstanceOf(MasterDnsDeployState.Done::class.java, state.deployState)
    }

    @Test
    fun `deploy error does not change configToml`() = runTest {
        val store = FakeStore(MasterDnsPersistedConfig(configToml = "original"))
        val vm = MasterDnsSettingsViewModel(
            store,
            FakeDeployer(
                MasterDnsDeployState.Connecting,
                MasterDnsDeployState.Error("connection_failed"),
            ),
        )
        vm.onDeployClick(host = "10.0.0.1", port = 22, login = "root", password = "pass".toCharArray())
        val state = vm.state.first { it.deployState is MasterDnsDeployState.Error }
        assertEquals("original", store.toml)
        assertInstanceOf(MasterDnsDeployState.Error::class.java, state.deployState)
    }

    @Test
    fun `onDeployReset resets deployState to Idle`() = runTest {
        val store = FakeStore(MasterDnsPersistedConfig())
        val vm = MasterDnsSettingsViewModel(
            store,
            FakeDeployer(MasterDnsDeployState.Error("connection_failed")),
        )
        vm.onDeployClick(host = "10.0.0.1", port = 22, login = "root", password = "pass".toCharArray())
        vm.state.first { it.deployState is MasterDnsDeployState.Error }
        vm.onDeployReset()
        val state = vm.state.first { it.deployState is MasterDnsDeployState.Idle }
        assertInstanceOf(MasterDnsDeployState.Idle::class.java, state.deployState)
    }

    @Test
    fun `deployState not contains password after Done`() = runTest {
        val toml = "ENCRYPTION_KEY = \"abc\""
        val store = FakeStore(MasterDnsPersistedConfig())
        val vm = MasterDnsSettingsViewModel(
            store,
            FakeDeployer(MasterDnsDeployState.Done(toml)),
        )
        vm.onDeployClick(host = "10.0.0.1", port = 22, login = "root", password = "top_secret".toCharArray())
        val state = vm.state.first { it.deployState is MasterDnsDeployState.Done }
        assertFalse(state.toString().contains("top_secret"))
        assertFalse(state.deployState.toString().contains("top_secret"))
    }

    private class FakeStore(initial: MasterDnsPersistedConfig) : MasterDnsConfigStore {
        private val flow = MutableStateFlow(initial)
        var enabled: Boolean = initial.enabled
        var toml: String = initial.configToml
        var resolvers: List<String> = initial.resolvers
        var serverIp: String = initial.serverIp
        var serverPort: Int = initial.serverPort
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
        override suspend fun setServerIp(ip: String) {
            this.serverIp = ip
            flow.value = flow.value.copy(serverIp = ip)
        }
        override suspend fun setServerPort(port: Int) {
            this.serverPort = port
            flow.value = flow.value.copy(serverPort = port)
        }
    }

    private class FakeDeployer(vararg states: MasterDnsDeployState) : MasterDnsServerDeployer {
        private val stateList = states.toList()
        override fun deploy(credentials: MasterDnsDeployCredentials): Flow<MasterDnsDeployState> =
            flowOf(*stateList.toTypedArray())
    }
}
