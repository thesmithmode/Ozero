package ru.ozero.enginemasterdns.deploy

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class MasterDnsDeployerTest {

    private lateinit var transport: FakeSshTransport
    private lateinit var deployer: FakeSshMasterDnsDeployer

    @BeforeEach
    fun setUp() {
        transport = FakeSshTransport()
        deployer = FakeSshMasterDnsDeployer(transport)
        setupHappyPath()
    }

    private fun setupHappyPath() {
        transport.setResponse("ss -uln", MasterDnsDockerScripts.MARKER_PORT_FREE)
        transport.setResponse("free -m", "512 1024")
        transport.setResponse("apt-get", MasterDnsDockerScripts.MARKER_DOCKER_OK)
        transport.setResponse("Dockerfile", MasterDnsDockerScripts.MARKER_BUILD_OK)
        transport.setResponse("docker rm -f", MasterDnsDockerScripts.MARKER_RUN_OK)
        transport.setResponse("encrypt_key", "aabbccddeeff00112233445566778899aabbccddeeff00112233445566778899")
    }

    private fun credentials(password: String = "secret") = MasterDnsDeployCredentials(
        host = "10.0.0.1",
        port = 22,
        login = "root",
        password = password.toCharArray(),
    )

    @Test
    fun `should emit state sequence Connecting through Done on happy path`() = runTest {
        val states = deployer.deploy(credentials()).toList()

        assertTrue(states[0] is MasterDnsDeployState.Connecting)
        assertTrue(states[1] is MasterDnsDeployState.CheckingPreflight)
        assertTrue(states[2] is MasterDnsDeployState.InstallingDocker)
        assertTrue(states[3] is MasterDnsDeployState.BuildingImage)
        assertTrue(states[4] is MasterDnsDeployState.StartingContainer)
        assertTrue(states[5] is MasterDnsDeployState.ExtractingKey)
        assertInstanceOf(MasterDnsDeployState.Done::class.java, states[6])
    }

    @Test
    fun `should include encryption key in Done configToml`() = runTest {
        val states = deployer.deploy(credentials()).toList()
        val done = states.last() as MasterDnsDeployState.Done

        assertTrue(done.configToml.contains("aabbccddeeff00112233445566778899aabbccddeeff00112233445566778899"))
    }

    @Test
    fun `should return Error when port 53 is busy`() = runTest {
        transport.setResponse("ss -uln", MasterDnsDockerScripts.MARKER_PORT_BUSY)

        val states = deployer.deploy(credentials()).toList()

        assertInstanceOf(MasterDnsDeployState.Error::class.java, states.last())
        val error = states.last() as MasterDnsDeployState.Error
        assertTrue(error.message == "port_53_busy")
    }

    @Test
    fun `should return Error when free RAM is below threshold`() = runTest {
        transport.setResponse("free -m", "128 2048")

        val states = deployer.deploy(credentials()).toList()

        val error = states.last() as MasterDnsDeployState.Error
        assertEquals("insufficient_resources", error.message)
    }

    @Test
    fun `should return Error when free disk is below threshold`() = runTest {
        transport.setResponse("free -m", "1024 100")

        val states = deployer.deploy(credentials()).toList()

        val error = states.last() as MasterDnsDeployState.Error
        assertEquals("insufficient_resources", error.message)
    }

    @Test
    fun `should return Error when no package manager found`() = runTest {
        transport.setResponse("apt-get", MasterDnsDockerScripts.MARKER_ERR_NO_PM)

        val states = deployer.deploy(credentials()).toList()

        val error = states.last() as MasterDnsDeployState.Error
        assertEquals("docker_install_failed", error.message)
    }

    @Test
    fun `should return Error when docker install fails`() = runTest {
        transport.setResponse("apt-get", MasterDnsDockerScripts.MARKER_ERR_DOCKER)

        val states = deployer.deploy(credentials()).toList()

        val error = states.last() as MasterDnsDeployState.Error
        assertEquals("docker_install_failed", error.message)
    }

    @Test
    fun `should emit sudo_not_installed error when sudo binary absent`() = runTest {
        transport.setResponse("sudo -K", MasterDnsDockerScripts.MARKER_ERR_SUDO_NOT_INSTALLED)
        val states = deployer.deploy(credentials()).toList()
        val error = states.last() as MasterDnsDeployState.Error
        assertEquals("sudo_not_installed", error.message)
    }

    @Test
    fun `should emit sudo_pwd_required error when password needed`() = runTest {
        transport.setResponse("sudo -K", MasterDnsDockerScripts.MARKER_ERR_SUDO_PWD_REQUIRED)
        val states = deployer.deploy(credentials()).toList()
        val error = states.last() as MasterDnsDeployState.Error
        assertEquals("sudo_pwd_required", error.message)
    }

    @Test
    fun `should emit sudo_not_allowed error when user denied in sudoers`() = runTest {
        transport.setResponse("sudo -K", MasterDnsDockerScripts.MARKER_ERR_SUDO_NOT_ALLOWED)
        val states = deployer.deploy(credentials()).toList()
        val error = states.last() as MasterDnsDeployState.Error
        assertEquals("sudo_not_allowed", error.message)
    }

    @Test
    fun `should emit sudo_no_home error when home dir inaccessible`() = runTest {
        transport.setResponse("sudo -K", MasterDnsDockerScripts.MARKER_ERR_SUDO_NO_HOME)
        val states = deployer.deploy(credentials()).toList()
        val error = states.last() as MasterDnsDeployState.Error
        assertEquals("sudo_no_home", error.message)
    }

    @Test
    fun `should emit sudo_not_in_group error when user not in sudo group`() = runTest {
        transport.setResponse("sudo -K", MasterDnsDockerScripts.MARKER_ERR_SUDO_NOT_IN_GROUP)
        val states = deployer.deploy(credentials()).toList()
        val error = states.last() as MasterDnsDeployState.Error
        assertEquals("sudo_not_in_group", error.message)
    }

    @Test
    fun `should emit distinct dpkg_locked error when apt lock polling exhausts`() = runTest {
        transport.setResponse("apt-get", MasterDnsDockerScripts.MARKER_ERR_DPKG_LOCKED)

        val states = deployer.deploy(credentials()).toList()

        val error = states.last() as MasterDnsDeployState.Error
        assertEquals(
            "dpkg_locked",
            error.message,
            "ERR_DPKG_LOCKED marker должен mapи в distinct error 'dpkg_locked' — не сваливать в " +
                "общий 'docker_install_failed', иначе UI покажет misleading 'не могу установить docker'.",
        )
    }

    @Test
    fun `should return Error when docker build fails`() = runTest {
        transport.setResponse("Dockerfile", MasterDnsDockerScripts.MARKER_ERR_BUILD)

        val states = deployer.deploy(credentials()).toList()

        val error = states.last() as MasterDnsDeployState.Error
        assertEquals("build_failed", error.message)
    }

    @Test
    fun `should return Error when container run fails`() = runTest {
        transport.setResponse("docker rm -f", MasterDnsDockerScripts.MARKER_ERR_RUN)

        val states = deployer.deploy(credentials()).toList()

        val error = states.last() as MasterDnsDeployState.Error
        assertEquals("run_failed", error.message)
    }

    @Test
    fun `should return Error when key extraction returns empty`() = runTest {
        transport.setResponse("encrypt_key", "")

        val states = deployer.deploy(credentials()).toList()

        val error = states.last() as MasterDnsDeployState.Error
        assertEquals("key_extraction_failed", error.message)
    }

    @Test
    fun `should return Error when SSH connection fails`() = runTest {
        transport.connectShouldFail = true

        val states = deployer.deploy(credentials()).toList()

        val error = states.last() as MasterDnsDeployState.Error
        assertEquals("connection_failed", error.message)
    }

    @Test
    fun `should return Error when SSH auth fails`() = runTest {
        transport.authShouldFail = true

        val states = deployer.deploy(credentials()).toList()

        val error = states.last() as MasterDnsDeployState.Error
        assertEquals("auth_failed", error.message)
    }

    @Test
    fun `should not include password in any executed command`() = runTest {
        val password = "super_secret_p@ssw0rd"
        deployer.deploy(credentials(password)).toList()

        for (cmd in transport.executedCommands) {
            assertFalse(
                cmd.contains(password),
                "Command contains password: $cmd",
            )
        }
    }

    @Test
    fun `should not include password in Done state`() = runTest {
        val password = "super_secret_p@ssw0rd"
        val states = deployer.deploy(credentials(password)).toList()
        val done = states.last() as MasterDnsDeployState.Done

        assertFalse(done.configToml.contains(password))
        assertFalse(done.toString().contains(password))
    }

    @Test
    fun `should be idempotent when deploying twice`() = runTest {
        deployer.deploy(credentials()).toList()
        val states = deployer.deploy(credentials()).toList()

        assertInstanceOf(MasterDnsDeployState.Done::class.java, states.last())
    }

    @Test
    fun `configToml should contain required TOML keys`() = runTest {
        val states = deployer.deploy(credentials()).toList()
        val done = states.last() as MasterDnsDeployState.Done

        assertTrue(done.configToml.contains("PROTOCOL_TYPE"))
        assertTrue(done.configToml.contains("DATA_ENCRYPTION_METHOD"))
        assertTrue(done.configToml.contains("RESOLVER_BALANCING_STRATEGY"))
        assertTrue(done.configToml.contains("ENCRYPTION_KEY"))
    }

    @Test
    fun `credentials toString should not expose password`() {
        val creds = credentials("top_secret")
        assertFalse(creds.toString().contains("top_secret"))
        assertTrue(creds.toString().contains("***"))
    }

    @Test
    fun `deployMasterDns script captures docker build exit code not bare pipe`() {
        assertFalse(
            MasterDnsDockerScripts.deployMasterDns.contains("| tail") &&
                !MasterDnsDockerScripts.deployMasterDns.contains("PIPESTATUS"),
            "deployMasterDns must use PIPESTATUS to capture docker build exit code — bare pipe loses it",
        )
        assertTrue(MasterDnsDockerScripts.deployMasterDns.contains("PIPESTATUS"))
    }

    @Test
    fun `should call transport close on connection failure during deploy`() = runTest {
        transport.connectShouldFail = true
        deployer.deploy(credentials()).toList()
        assertTrue(transport.closeCalled)
    }

    @Test
    fun `should call transport close on connection failure during undeploy`() = runTest {
        transport.connectShouldFail = true
        deployer.undeploy(credentials()).toList()
        assertTrue(transport.closeCalled)
    }

    @Test
    fun `should return Error when undeploy remove command fails`() = runTest {
        val states = deployer.undeploy(credentials()).toList()
        val error = states.last() as MasterDnsDeployState.Error
        assertEquals("remove_failed", error.message)
    }

    @Test
    fun `should emit Removing then Removed on successful undeploy`() = runTest {
        transport.setResponse("docker rm -f", MasterDnsDockerScripts.MARKER_REMOVE_OK)
        val states = deployer.undeploy(credentials()).toList()
        assertTrue(states.any { it is MasterDnsDeployState.Removing })
        assertInstanceOf(MasterDnsDeployState.Removed::class.java, states.last())
    }

    @Test
    fun `deploy clears credentials password when connect fails`() = runTest {
        transport.connectShouldFail = true
        val creds = credentials("super_secret_p@ssw0rd")
        deployer.deploy(creds).toList()
        assertTrue(creds.password.all { it == ' ' }, "password must be wiped after connect failure")
    }

    @Test
    fun `undeploy clears credentials password when connect fails`() = runTest {
        transport.connectShouldFail = true
        val creds = credentials("super_secret_p@ssw0rd")
        deployer.undeploy(creds).toList()
        assertTrue(creds.password.all { it == ' ' }, "password must be wiped after connect failure")
    }
}
