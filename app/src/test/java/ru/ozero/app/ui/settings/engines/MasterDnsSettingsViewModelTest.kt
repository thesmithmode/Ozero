package ru.ozero.app.ui.settings.engines

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
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
            MasterDnsPersistedConfig(configToml = "x", resolvers = listOf("8.8.8.8")),
        )
        val vm = MasterDnsSettingsViewModel(store, FakeDeployer())
        val state = vm.state.first { it.configToml == "x" }
        assertEquals("x", state.configToml)
        assertEquals("8.8.8.8", state.resolversText)
    }

    @Test
    fun `resolvers serialized as newline-joined`() = runTest {
        val store = FakeStore(
            MasterDnsPersistedConfig(resolvers = listOf("8.8.8.8", "1.1.1.1")),
        )
        val vm = MasterDnsSettingsViewModel(store, FakeDeployer())
        val state = vm.state.first { it.resolversText.isNotEmpty() }
        assertEquals("8.8.8.8\n1.1.1.1", state.resolversText)
    }

    @Test
    fun `onResolversChange splits by newline and trims`() = runTest {
        val store = FakeStore(MasterDnsPersistedConfig())
        val vm = MasterDnsSettingsViewModel(store, FakeDeployer())
        vm.onResolversChange("8.8.8.8\n 1.1.1.1 \n\n")
        assertEquals(listOf("8.8.8.8", "1.1.1.1"), store.resolvers)
    }

    @Test
    fun `onResolversChange with empty input clears`() = runTest {
        val store = FakeStore(MasterDnsPersistedConfig(resolvers = listOf("8.8.8.8")))
        val vm = MasterDnsSettingsViewModel(store, FakeDeployer())
        vm.onResolversChange("   \n  \n")
        assertTrue(store.resolvers.isEmpty())
    }

    @Test
    fun `onConfigTomlChange persists`() = runTest {
        val store = FakeStore(MasterDnsPersistedConfig())
        val vm = MasterDnsSettingsViewModel(store, FakeDeployer())
        vm.onConfigTomlChange("DOMAINS=[]")
        assertEquals("DOMAINS=[]", store.toml)
    }

    @Test
    fun `state includes serverIp and serverPort from store`() = runTest {
        val store = FakeStore(MasterDnsPersistedConfig(serverIp = "1.2.3.4", serverPort = 2222))
        val vm = MasterDnsSettingsViewModel(store, FakeDeployer())
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
                deployStates = listOf(
                    MasterDnsDeployState.Connecting,
                    MasterDnsDeployState.CheckingPreflight,
                    MasterDnsDeployState.InstallingDocker,
                    MasterDnsDeployState.BuildingImage,
                    MasterDnsDeployState.StartingContainer,
                    MasterDnsDeployState.ExtractingKey,
                    MasterDnsDeployState.Done(toml),
                ),
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
    fun `deploy success auto-fills resolvers with serverIp colon 53`() = runTest {
        val store = FakeStore(MasterDnsPersistedConfig())
        val vm = MasterDnsSettingsViewModel(
            store,
            FakeDeployer(deployStates = listOf(MasterDnsDeployState.Done("toml"))),
        )
        vm.onDeployClick(host = "5.6.7.8", port = 22, login = "root", password = "pass".toCharArray())
        vm.state.first { it.deployState is MasterDnsDeployState.Done }
        assertEquals(listOf("5.6.7.8:53"), store.resolvers)
    }

    @Test
    fun `deploy log contains step descriptions for happy path`() = runTest {
        val store = FakeStore(MasterDnsPersistedConfig())
        val vm = MasterDnsSettingsViewModel(
            store,
            FakeDeployer(
                deployStates = listOf(
                    MasterDnsDeployState.Connecting,
                    MasterDnsDeployState.CheckingPreflight,
                    MasterDnsDeployState.InstallingDocker,
                    MasterDnsDeployState.BuildingImage,
                    MasterDnsDeployState.StartingContainer,
                    MasterDnsDeployState.ExtractingKey,
                    MasterDnsDeployState.Done("toml"),
                ),
            ),
        )
        vm.onDeployClick(host = "9.9.9.9", port = 22, login = "root", password = "p".toCharArray())
        val state = vm.state.first { it.deployState is MasterDnsDeployState.Done }
        val combined = state.deployLog.joinToString("\n")
        assertTrue(combined.contains("9.9.9.9"), "лог должен содержать host: $combined")
        assertTrue(combined.contains("Docker"), "лог должен описать шаг установки Docker: $combined")
        assertTrue(combined.contains("encrypt_key") || combined.contains("ключ"), combined)
        assertTrue(combined.contains("Резолверы") || combined.contains("✓"), combined)
    }

    @Test
    fun `deploy log error step shown for user`() = runTest {
        val store = FakeStore(MasterDnsPersistedConfig())
        val vm = MasterDnsSettingsViewModel(
            store,
            FakeDeployer(
                deployStates = listOf(
                    MasterDnsDeployState.Connecting,
                    MasterDnsDeployState.Error("port_53_busy"),
                ),
            ),
        )
        vm.onDeployClick(host = "1.1.1.1", port = 22, login = "root", password = "p".toCharArray())
        val state = vm.state.first { it.deployState is MasterDnsDeployState.Error }
        assertTrue(
            state.deployLog.any { it.contains("port_53_busy") },
            "лог должен показать причину ошибки: ${state.deployLog}",
        )
    }

    @Test
    fun `onDeployReset clears deploy log`() = runTest {
        val store = FakeStore(MasterDnsPersistedConfig())
        val vm = MasterDnsSettingsViewModel(
            store,
            FakeDeployer(deployStates = listOf(MasterDnsDeployState.Connecting, MasterDnsDeployState.Done("t"))),
        )
        vm.onDeployClick(host = "5.5.5.5", port = 22, login = "root", password = "p".toCharArray())
        vm.state.first { it.deployLog.isNotEmpty() }
        vm.onDeployReset()
        val state = vm.state.first { it.deployLog.isEmpty() }
        assertTrue(state.deployLog.isEmpty())
    }

    @Test
    fun `deploy error does not change configToml`() = runTest {
        val store = FakeStore(MasterDnsPersistedConfig(configToml = "original"))
        val vm = MasterDnsSettingsViewModel(
            store,
            FakeDeployer(
                deployStates = listOf(
                    MasterDnsDeployState.Connecting,
                    MasterDnsDeployState.Error("connection_failed"),
                ),
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
            FakeDeployer(deployStates = listOf(MasterDnsDeployState.Error("connection_failed"))),
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
            FakeDeployer(deployStates = listOf(MasterDnsDeployState.Done(toml))),
        )
        vm.onDeployClick(host = "10.0.0.1", port = 22, login = "root", password = "top_secret".toCharArray())
        val state = vm.state.first { it.deployState is MasterDnsDeployState.Done }
        assertFalse(state.toString().contains("top_secret"))
        assertFalse(state.deployState.toString().contains("top_secret"))
    }

    @Test
    fun `undeploy success sets Removed and clears serverIp and serverPort`() = runTest {
        val store = FakeStore(MasterDnsPersistedConfig(serverIp = "10.0.0.1", serverPort = 2222))
        val vm = MasterDnsSettingsViewModel(
            store,
            FakeDeployer(
                undeployStates = listOf(
                    MasterDnsDeployState.Connecting,
                    MasterDnsDeployState.Removing,
                    MasterDnsDeployState.Removed,
                ),
            ),
        )
        vm.onUndeployClick(host = "10.0.0.1", port = 2222, login = "root", password = "pass".toCharArray())
        val state = vm.state.first { it.deployState is MasterDnsDeployState.Removed }
        assertEquals("", store.serverIp)
        assertEquals(22, store.serverPort)
        assertInstanceOf(MasterDnsDeployState.Removed::class.java, state.deployState)
    }

    @Test
    fun `undeploy error does not clear serverIp`() = runTest {
        val store = FakeStore(MasterDnsPersistedConfig(serverIp = "10.0.0.1", serverPort = 22))
        val vm = MasterDnsSettingsViewModel(
            store,
            FakeDeployer(
                undeployStates = listOf(
                    MasterDnsDeployState.Connecting,
                    MasterDnsDeployState.Error("remove_failed"),
                ),
            ),
        )
        vm.onUndeployClick(host = "10.0.0.1", port = 22, login = "root", password = "pass".toCharArray())
        vm.state.first { it.deployState is MasterDnsDeployState.Error }
        assertEquals("10.0.0.1", store.serverIp)
    }

    @Test
    fun `second deploy click while in flight is ignored`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val store = FakeStore(MasterDnsPersistedConfig())
        var deployCallCount = 0
        val deployer = object : MasterDnsServerDeployer {
            override fun deploy(credentials: MasterDnsDeployCredentials): Flow<MasterDnsDeployState> {
                deployCallCount++
                return flow {
                    gate.await()
                    emit(MasterDnsDeployState.Idle)
                }
            }
            override fun undeploy(credentials: MasterDnsDeployCredentials) =
                flowOf(MasterDnsDeployState.Idle)
        }
        val vm = MasterDnsSettingsViewModel(store, deployer)
        vm.onDeployClick("10.0.0.1", 22, "root", "pass".toCharArray())
        vm.onDeployClick("10.0.0.1", 22, "root", "pass".toCharArray())
        assertEquals(1, deployCallCount)
        gate.complete(Unit)
    }

    @Test
    fun `undeploy click while deploy in flight is ignored`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val store = FakeStore(MasterDnsPersistedConfig())
        var undeployCallCount = 0
        val deployer = object : MasterDnsServerDeployer {
            override fun deploy(credentials: MasterDnsDeployCredentials): Flow<MasterDnsDeployState> =
                flow {
                    gate.await()
                    emit(MasterDnsDeployState.Idle)
                }
            override fun undeploy(credentials: MasterDnsDeployCredentials): Flow<MasterDnsDeployState> {
                undeployCallCount++
                return flowOf(MasterDnsDeployState.Idle)
            }
        }
        val vm = MasterDnsSettingsViewModel(store, deployer)
        vm.onDeployClick("10.0.0.1", 22, "root", "pass".toCharArray())
        vm.onUndeployClick("10.0.0.1", 22, "root", "pass".toCharArray())
        assertEquals(0, undeployCallCount)
        gate.complete(Unit)
    }

    @Test
    fun `deploy click while undeploy in flight is ignored`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val store = FakeStore(MasterDnsPersistedConfig())
        var deployCallCount = 0
        val deployer = object : MasterDnsServerDeployer {
            override fun deploy(credentials: MasterDnsDeployCredentials): Flow<MasterDnsDeployState> {
                deployCallCount++
                return flowOf(MasterDnsDeployState.Idle)
            }
            override fun undeploy(credentials: MasterDnsDeployCredentials): Flow<MasterDnsDeployState> =
                flow {
                    gate.await()
                    emit(MasterDnsDeployState.Idle)
                }
        }
        val vm = MasterDnsSettingsViewModel(store, deployer)
        vm.onUndeployClick("10.0.0.1", 22, "root", "pass".toCharArray())
        vm.onDeployClick("10.0.0.1", 22, "root", "pass".toCharArray())
        assertEquals(0, deployCallCount)
        gate.complete(Unit)
    }

    @Test
    fun `deploy flow throws sets Error unexpected_error state`() = runTest {
        val store = FakeStore(MasterDnsPersistedConfig())
        val deployer = object : MasterDnsServerDeployer {
            override fun deploy(credentials: MasterDnsDeployCredentials): Flow<MasterDnsDeployState> = flow {
                error("boom")
            }
            override fun undeploy(credentials: MasterDnsDeployCredentials) =
                flowOf(MasterDnsDeployState.Idle)
        }
        val vm = MasterDnsSettingsViewModel(store, deployer)
        vm.onDeployClick("10.0.0.1", 22, "root", "pass".toCharArray())
        val state = vm.state.first { it.deployState is MasterDnsDeployState.Error }
        assertEquals("unexpected_error", (state.deployState as MasterDnsDeployState.Error).message)
    }

    @Test
    fun `onDeployReset cancels in-flight undeploy`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val store = FakeStore(MasterDnsPersistedConfig())
        val deployer = object : MasterDnsServerDeployer {
            override fun deploy(credentials: MasterDnsDeployCredentials) =
                flowOf(MasterDnsDeployState.Idle)
            override fun undeploy(credentials: MasterDnsDeployCredentials): Flow<MasterDnsDeployState> = flow {
                emit(MasterDnsDeployState.Removing)
                gate.await()
                emit(MasterDnsDeployState.Removed)
            }
        }
        val vm = MasterDnsSettingsViewModel(store, deployer)
        vm.onUndeployClick("10.0.0.1", 22, "root", "pass".toCharArray())
        vm.state.first { it.deployState is MasterDnsDeployState.Removing }
        vm.onDeployReset()
        val state = vm.state.first { it.deployState is MasterDnsDeployState.Idle }
        assertInstanceOf(MasterDnsDeployState.Idle::class.java, state.deployState)
        gate.complete(Unit)
    }

    private class FakeStore(initial: MasterDnsPersistedConfig) : MasterDnsConfigStore {
        private val flow = MutableStateFlow(initial)
        var toml: String = initial.configToml
        var resolvers: List<String> = initial.resolvers
        var serverIp: String = initial.serverIp
        var serverPort: Int = initial.serverPort
        override fun config() = flow
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

    private class FakeDeployer(
        private val deployStates: List<MasterDnsDeployState> = listOf(MasterDnsDeployState.Idle),
        private val undeployStates: List<MasterDnsDeployState> = listOf(MasterDnsDeployState.Idle),
    ) : MasterDnsServerDeployer {
        override fun deploy(credentials: MasterDnsDeployCredentials): Flow<MasterDnsDeployState> =
            flowOf(*deployStates.toTypedArray())

        override fun undeploy(credentials: MasterDnsDeployCredentials): Flow<MasterDnsDeployState> =
            flowOf(*undeployStates.toTypedArray())
    }
}
