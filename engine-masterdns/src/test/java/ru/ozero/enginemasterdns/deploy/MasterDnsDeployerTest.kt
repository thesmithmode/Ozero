@file:Suppress("LargeClass")

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
    private val readEncryptKeyCommand = "docker exec masterdns-ozero cat /etc/masterdnsvpn/encrypt_key.txt"

    private lateinit var transport: FakeSshTransport
    private lateinit var deployer: FakeSshMasterDnsDeployer

    @BeforeEach
    fun setUp() {
        transport = FakeSshTransport()
        deployer = FakeSshMasterDnsDeployer(transport)
        setupHappyPath()
    }

    private fun setupHappyPath() {
        transport.setResponse("bind_probe", MasterDnsDockerScripts.MARKER_PORT_FREE)
        transport.setResponse("free -m", "512 1024")
        transport.setResponse("apt-get", MasterDnsDockerScripts.MARKER_DOCKER_OK)
        transport.setResponse("Dockerfile", MasterDnsDockerScripts.MARKER_BUILD_OK)
        transport.setResponse("docker run -d", MasterDnsDockerScripts.MARKER_RUN_OK)
        transport.setResponse(
            readEncryptKeyCommand,
            "aabbccddeeff00112233445566778899aabbccddeeff00112233445566778899",
        )
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
        transport.setResponse("bind_probe", MasterDnsDockerScripts.MARKER_PORT_BUSY)

        val states = deployer.deploy(credentials()).toList()

        assertInstanceOf(MasterDnsDeployState.Error::class.java, states.last())
        val error = states.last() as MasterDnsDeployState.Error
        assertTrue(error.message.startsWith("port_53_busy"))
    }

    @Test
    fun `should emit AmneziaDnsConflict when amnezia-dns publishes udp 53`() = runTest {
        transport.setResponse(
            "docker inspect amnezia-dns",
            "AMNEZIA_DNS_CONFLICT|proto=udp|addr=0.0.0.0",
        )

        val states = deployer.deploy(credentials()).toList()

        val conflict = states.last() as MasterDnsDeployState.AmneziaDnsConflict
        assertEquals("udp", conflict.protocol)
        assertEquals("0.0.0.0", conflict.address)
        assertFalse(transport.executedCommands.any { it.contains("docker stop amnezia-dns") })
        assertFalse(transport.executedCommands.any { it.contains("docker rm amnezia-dns") })
    }

    @Test
    fun `cancel path does not stop or remove amnezia-dns`() = runTest {
        transport.setResponse(
            "docker inspect amnezia-dns",
            "AMNEZIA_DNS_CONFLICT|proto=udp|addr=0.0.0.0",
        )

        deployer.deploy(credentials()).toList()

        assertFalse(transport.executedCommands.any { it.contains("docker stop amnezia-dns") })
        assertFalse(transport.executedCommands.any { it.contains("docker rm amnezia-dns") })
    }

    @Test
    fun `remove path calls only inspect stop rm for amnezia-dns before continuing`() = runTest {
        transport.setResponse("docker inspect amnezia-dns", MasterDnsDockerScripts.MARKER_AMNEZIA_DNS_REMOVED)

        val states = deployer.removeAmneziaDnsAndContinue(credentials()).toList()

        assertInstanceOf(MasterDnsDeployState.Done::class.java, states.last())
        val amneziaCommands = transport.executedCommands.filter { it.contains("amnezia-dns") }
        assertTrue(
            amneziaCommands.all {
                it.contains("docker inspect amnezia-dns") ||
                    it.contains("docker stop amnezia-dns") ||
                    it.contains("docker rm amnezia-dns")
            },
        )
        assertFalse(amneziaCommands.any { it.contains("system prune") })
        assertFalse(
            amneziaCommands.any { it.contains("volume rm") || it.contains("network rm") || it.contains("rmi") },
        )
    }

    @Test
    fun `remove path stops when amnezia-dns removal script reports failure`() = runTest {
        transport.setResponse("docker inspect amnezia-dns", MasterDnsDockerScripts.MARKER_AMNEZIA_DNS_REMOVE_FAILED)

        val states = deployer.removeAmneziaDnsAndContinue(credentials()).toList()

        val error = states.last() as MasterDnsDeployState.Error
        assertEquals("amnezia_dns_remove_failed", error.message)
        assertFalse(transport.executedCommands.any { it.contains("bind_probe") })
        assertFalse(transport.executedCommands.any { it.contains("apt-get") })
        assertFalse(transport.executedCommands.any { it.contains("docker run -d") })
    }

    @Test
    fun `should emit PortBusy when port 53 check emits structured owner`() = runTest {
        transport.setResponse("bind_probe", "PORT_BUSY|proto=udp|addr=0.0.0.0:53|owner=docker:adguardhome")

        val states = deployer.deploy(credentials()).toList()

        val portBusy = states.last() as MasterDnsDeployState.PortBusy
        assertEquals("udp", portBusy.protocol)
        assertEquals("0.0.0.0:53", portBusy.address)
        assertEquals("docker:adguardhome", portBusy.owner)
    }

    @Test
    fun `should map legacy name field to PortBusy owner`() = runTest {
        transport.setResponse("bind_probe", "PORT_BUSY|proto=udp|addr=0.0.0.0|name=docker-proxy")

        val states = deployer.deploy(credentials()).toList()

        val portBusy = states.last() as MasterDnsDeployState.PortBusy
        assertEquals("udp", portBusy.protocol)
        assertEquals("0.0.0.0", portBusy.address)
        assertEquals("docker-proxy", portBusy.owner)
    }

    @Test
    fun `should stop before docker run when bind probe reports port conflict without process owner`() = runTest {
        transport.setResponse("bind_probe", "PORT_BUSY|proto=udp|addr=0.0.0.0:53|owner=bind_probe:exit_98")

        val states = deployer.deploy(credentials()).toList()

        val portBusy = states.last() as MasterDnsDeployState.PortBusy
        assertEquals("udp", portBusy.protocol)
        assertEquals("0.0.0.0:53", portBusy.address)
        assertEquals("bind_probe:exit_98", portBusy.owner)
        assertFalse(transport.executedCommands.any { it.contains("docker run -d") })
    }

    @Test
    fun `malformed structured port busy falls back to generic port error`() = runTest {
        transport.setResponse("bind_probe", "PORT_BUSY|proto=udp|addr=0.0.0.0:53|owner=")

        val states = deployer.deploy(credentials()).toList()

        val error = states.last() as MasterDnsDeployState.Error
        assertEquals("port_53_busy|proto=udp|addr=0.0.0.0:53|owner=", error.message)
        assertFalse(transport.executedCommands.any { it.contains("apt-get") })
    }

    @Test
    fun `structured port busy with missing proto or address falls back to raw generic error`() = runTest {
        transport.setResponse("bind_probe", "PORT_BUSY|proto=|addr=|owner=dnsmasq")

        val states = deployer.deploy(credentials()).toList()

        val error = states.last() as MasterDnsDeployState.Error
        assertEquals("port_53_busy|proto=|addr=|owner=dnsmasq", error.message)
        assertFalse(transport.executedCommands.any { it.contains("apt-get") })
    }

    @Test
    fun `legacy port busy marker maps to generic port error without details`() = runTest {
        transport.setResponse("bind_probe", MasterDnsDockerScripts.MARKER_PORT_BUSY)

        val states = deployer.deploy(credentials()).toList()

        val error = states.last() as MasterDnsDeployState.Error
        assertEquals("port_53_busy", error.message)
    }

    @Test
    fun `amnezia dns conflict defaults missing marker fields`() = runTest {
        transport.setResponse("docker inspect amnezia-dns", MasterDnsDockerScripts.MARKER_AMNEZIA_DNS_CONFLICT)

        val states = deployer.deploy(credentials()).toList()

        val conflict = states.last() as MasterDnsDeployState.AmneziaDnsConflict
        assertEquals("unknown", conflict.protocol)
        assertEquals("0.0.0.0", conflict.address)
    }

    @Test
    fun `should recheck port after docker install before docker build and run`() = runTest {
        transport.setResponses(
            "bind_probe",
            listOf(
                MasterDnsDockerScripts.MARKER_PORT_FREE,
                "PORT_BUSY|proto=udp|addr=0.0.0.0:53|owner=docker:adguardhome",
            ),
        )

        val states = deployer.deploy(credentials()).toList()

        val portBusy = states.last() as MasterDnsDeployState.PortBusy
        assertEquals("docker:adguardhome", portBusy.owner)
        assertFalse(transport.executedCommands.any { it.contains("Dockerfile") })
        assertFalse(transport.executedCommands.any { it.contains("docker run -d") })
    }

    @Test
    fun `post docker amnezia dns conflict stops before build`() = runTest {
        transport.setResponses(
            "docker inspect amnezia-dns",
            listOf("", "AMNEZIA_DNS_CONFLICT|proto=udp|addr=0.0.0.0:53"),
        )

        val states = deployer.deploy(credentials()).toList()

        val conflict = states.last() as MasterDnsDeployState.AmneziaDnsConflict
        assertEquals("udp", conflict.protocol)
        assertEquals("0.0.0.0:53", conflict.address)
        assertFalse(transport.executedCommands.any { it.contains("Dockerfile") })
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
    fun `should return bin missing build error when docker build reports missing server binary`() = runTest {
        transport.setResponse(
            "Dockerfile",
            "ERR_BUILD|reason=bin_missing|ERR_BUILD_BIN_MISSING|candidates=none\n" +
                "--- docker-build.log tail -80 ---\nno release asset produced a server binary",
        )

        val states = deployer.deploy(credentials()).toList()

        val error = states.last() as MasterDnsDeployState.Error
        assertTrue(error.message.startsWith("build_failed/bin_missing|ERR_BUILD|reason=bin_missing"))
        assertTrue(error.message.contains("candidates=none"))
    }

    @Test
    fun `bin missing build error redacts password and token diagnostics`() = runTest {
        transport.setResponse(
            "Dockerfile",
            "ERR_BUILD|reason=bin_missing|password=secret-value|token=token-value|candidate=/tmp/bin",
        )

        val states = deployer.deploy(credentials()).toList()

        val error = states.last() as MasterDnsDeployState.Error
        assertTrue(error.message.startsWith("build_failed/bin_missing|ERR_BUILD|reason=bin_missing"))
        assertTrue(error.message.contains("password=<redacted>"))
        assertTrue(error.message.contains("token=<redacted>"))
        assertFalse(error.message.contains("secret-value"))
        assertFalse(error.message.contains("token-value"))
    }

    @Test
    fun `bin missing build marker without diagnostics uses compact error`() = runTest {
        transport.setResponse("Dockerfile", MasterDnsDockerScripts.MARKER_ERR_BUILD_BIN_MISSING)

        val states = deployer.deploy(credentials()).toList()

        val error = states.last() as MasterDnsDeployState.Error
        assertEquals("build_failed/bin_missing|${MasterDnsDockerScripts.MARKER_ERR_BUILD_BIN_MISSING}", error.message)
    }

    @Test
    fun `bin missing reason without marker line uses compact bin missing error`() = runTest {
        transport.setResponse("Dockerfile", "some log\nreason=bin_missing\nmore log")

        val states = deployer.deploy(credentials()).toList()

        val error = states.last() as MasterDnsDeployState.Error
        assertEquals("build_failed/bin_missing", error.message)
    }

    @Test
    fun `should return Error when container run fails`() = runTest {
        transport.setResponse("docker run -d", MasterDnsDockerScripts.MARKER_ERR_RUN)

        val states = deployer.deploy(credentials()).toList()

        val error = states.last() as MasterDnsDeployState.Error
        assertEquals("run_failed", error.message)
    }

    @Test
    fun `should keep real run failure diagnostics in error message`() = runTest {
        transport.setResponse(
            "docker run -d",
            "ERR_RUN|phase=docker_run|exit=127|state=created|exit=127|" +
                "error=exec \"/usr/local/bin/masterdnsvpn-server\": stat no such file or directory|logs=",
        )

        val states = deployer.deploy(credentials()).toList()

        val error = states.last() as MasterDnsDeployState.Error
        assertTrue(error.message.startsWith("run_failed|phase=docker_run|exit=127|state=created"))
        assertTrue(error.message.contains("masterdnsvpn-server"))
        assertTrue(error.message.contains("no such file or directory"))
    }

    @Test
    fun `run error marker with blank details maps to compact run failed`() = runTest {
        transport.setResponse("docker run -d", "${MasterDnsDockerScripts.MARKER_ERR_RUN}|")

        val states = deployer.deploy(credentials()).toList()

        val error = states.last() as MasterDnsDeployState.Error
        assertEquals("run_failed", error.message)
    }

    @Test
    fun `run failure without marker maps to compact run failed`() = runTest {
        transport.setResponse("docker run -d", "container exited before marker")

        val states = deployer.deploy(credentials()).toList()

        val error = states.last() as MasterDnsDeployState.Error
        assertEquals("run_failed", error.message)
    }

    @Test
    fun `firewall command failure is reported as unexpected error`() = runTest {
        transport.failOn("ufw")

        val states = deployer.deploy(credentials()).toList()

        val error = states.last() as MasterDnsDeployState.Error
        assertEquals("unexpected_error", error.message)
        assertTrue(transport.closeCalled)
    }

    @Test
    fun `should return Error when key extraction returns empty`() = runTest {
        transport.setResponse(readEncryptKeyCommand, "")

        val states = deployer.deploy(credentials()).toList()

        val error = states.last() as MasterDnsDeployState.Error
        assertEquals("key_extraction_failed", error.message)
    }

    @Test
    fun `should execute retrying readEncryptKey script during key extraction`() = runTest {
        deployer.deploy(credentials()).toList()

        assertTrue(
            transport.executedCommands.any {
                it.contains("seq 1 10") &&
                    it.contains("docker exec masterdns-ozero cat /etc/masterdnsvpn/encrypt_key.txt")
            },
        )
    }

    @Test
    fun `should return Error when SSH connection fails`() = runTest {
        transport.connectShouldFail = true

        val states = deployer.deploy(credentials()).toList()

        val error = states.last() as MasterDnsDeployState.Error
        assertEquals("connection_failed", error.message)
    }

    @Test
    fun `deploy returns unexpected error when command execution throws`() = runTest {
        transport.failOn("free -m")

        val states = deployer.deploy(credentials()).toList()

        val error = states.last() as MasterDnsDeployState.Error
        assertEquals("unexpected_error", error.message)
        assertTrue(transport.closeCalled)
    }

    @Test
    fun `should return Error when SSH auth fails`() = runTest {
        transport.authShouldFail = true

        val states = deployer.deploy(credentials()).toList()

        val error = states.last() as MasterDnsDeployState.Error
        assertEquals("auth_failed", error.message)
    }

    @Test
    fun `undeploy returns unexpected error when remove command throws`() = runTest {
        transport.failOn("docker rm -f")

        val states = deployer.undeploy(credentials()).toList()

        val error = states.last() as MasterDnsDeployState.Error
        assertEquals("unexpected_error", error.message)
        assertTrue(states.any { it is MasterDnsDeployState.Removing })
        assertTrue(transport.closeCalled)
    }

    @Test
    fun `remove and continue treats not found marker as successful removal`() = runTest {
        transport.setResponse("docker inspect amnezia-dns", MasterDnsDockerScripts.MARKER_AMNEZIA_DNS_NOT_FOUND)

        val states = deployer.removeAmneziaDnsAndContinue(credentials()).toList()

        assertTrue(states.any { it is MasterDnsDeployState.Removing })
        assertInstanceOf(MasterDnsDeployState.Done::class.java, states.last())
    }

    @Test
    fun `remove and continue returns unexpected error when remove command throws`() = runTest {
        transport.failOn("docker inspect amnezia-dns")

        val states = deployer.removeAmneziaDnsAndContinue(credentials()).toList()

        val error = states.last() as MasterDnsDeployState.Error
        assertEquals("unexpected_error", error.message)
        assertTrue(transport.closeCalled)
    }

    @Test
    fun `remove and continue clears credentials when connect fails`() = runTest {
        transport.connectShouldFail = true
        val creds = credentials("super_secret_p@ssw0rd")

        val states = deployer.removeAmneziaDnsAndContinue(creds).toList()

        val error = states.last() as MasterDnsDeployState.Error
        assertEquals("connection_failed", error.message)
        assertTrue(creds.password.all { it == '\u0000' })
        assertTrue(transport.closeCalled)
    }

    @Test
    fun `remove and continue stops when preflight fails after successful removal`() = runTest {
        transport.setResponse("docker inspect amnezia-dns", MasterDnsDockerScripts.MARKER_AMNEZIA_DNS_REMOVED)
        transport.setResponse("sudo -K", MasterDnsDockerScripts.MARKER_ERR_SUDO_NOT_ALLOWED)

        val states = deployer.removeAmneziaDnsAndContinue(credentials()).toList()

        val error = states.last() as MasterDnsDeployState.Error
        assertEquals("sudo_not_allowed", error.message)
        assertFalse(transport.executedCommands.any { it.contains("apt-get") })
    }

    @Test
    fun `remove and continue stops when docker install fails after successful removal`() = runTest {
        transport.setResponse("docker inspect amnezia-dns", MasterDnsDockerScripts.MARKER_AMNEZIA_DNS_REMOVED)
        transport.setResponse("apt-get", MasterDnsDockerScripts.MARKER_ERR_DOCKER)

        val states = deployer.removeAmneziaDnsAndContinue(credentials()).toList()

        val error = states.last() as MasterDnsDeployState.Error
        assertEquals("docker_install_failed", error.message)
        assertFalse(transport.executedCommands.any { it.contains("Dockerfile") })
    }

    @Test
    fun `remove and continue stops when post docker port check fails`() = runTest {
        transport.setResponse("docker inspect amnezia-dns", MasterDnsDockerScripts.MARKER_AMNEZIA_DNS_REMOVED)
        transport.setResponses(
            "bind_probe",
            listOf(
                MasterDnsDockerScripts.MARKER_PORT_FREE,
                "PORT_BUSY|proto=udp|addr=0.0.0.0:53|owner=docker:adguardhome",
            ),
        )

        val states = deployer.removeAmneziaDnsAndContinue(credentials()).toList()

        val portBusy = states.last() as MasterDnsDeployState.PortBusy
        assertEquals("docker:adguardhome", portBusy.owner)
        assertFalse(transport.executedCommands.any { it.contains("Dockerfile") })
    }

    @Test
    fun `remove and continue stops when build fails after successful removal`() = runTest {
        transport.setResponse("docker inspect amnezia-dns", MasterDnsDockerScripts.MARKER_AMNEZIA_DNS_REMOVED)
        transport.setResponse("Dockerfile", MasterDnsDockerScripts.MARKER_ERR_BUILD)

        val states = deployer.removeAmneziaDnsAndContinue(credentials()).toList()

        val error = states.last() as MasterDnsDeployState.Error
        assertEquals("build_failed", error.message)
        assertFalse(transport.executedCommands.any { it.contains("docker exec masterdns-ozero cat") })
    }

    @Test
    fun `remove and continue stops when key extraction fails after successful removal`() = runTest {
        transport.setResponse("docker inspect amnezia-dns", MasterDnsDockerScripts.MARKER_AMNEZIA_DNS_REMOVED)
        transport.setResponse(readEncryptKeyCommand, "")

        val states = deployer.removeAmneziaDnsAndContinue(credentials()).toList()

        val error = states.last() as MasterDnsDeployState.Error
        assertEquals("key_extraction_failed", error.message)
    }

    @Test
    fun `malformed resources output maps missing numbers to insufficient resources`() = runTest {
        transport.setResponse("free -m", "n/a")

        val states = deployer.deploy(credentials()).toList()

        val error = states.last() as MasterDnsDeployState.Error
        assertEquals("insufficient_resources", error.message)
        assertFalse(transport.executedCommands.any { it.contains("apt-get") })
    }

    @Test
    fun `both resources below threshold stop before docker install`() = runTest {
        transport.setResponse("free -m", "1 1")

        val states = deployer.deploy(credentials()).toList()

        val error = states.last() as MasterDnsDeployState.Error
        assertEquals("insufficient_resources", error.message)
        assertFalse(transport.executedCommands.any { it.contains("apt-get") })
    }

    @Test
    fun `firewall ok marker completes without warning path`() = runTest {
        transport.setResponse("ufw", "FW_OK")

        val states = deployer.deploy(credentials()).toList()

        assertInstanceOf(MasterDnsDeployState.Done::class.java, states.last())
    }

    @Test
    fun `firewall none marker is non fatal`() = runTest {
        transport.setResponse("ufw", MasterDnsDockerScripts.MARKER_FW_NONE_OK)

        val states = deployer.deploy(credentials()).toList()

        assertInstanceOf(MasterDnsDeployState.Done::class.java, states.last())
    }

    @Test
    fun `long bin missing diagnostics are truncated and sanitized`() = runTest {
        val longSecret = "x".repeat(900)
        transport.setResponse(
            "Dockerfile",
            "ERR_BUILD|reason=bin_missing|password=$longSecret|token=$longSecret|candidate=/tmp/bin",
        )

        val states = deployer.deploy(credentials()).toList()

        val error = states.last() as MasterDnsDeployState.Error
        assertTrue(error.message.length < 780)
        assertTrue(error.message.contains("password=<redacted>"))
        assertTrue(error.message.contains("token=<redacted>"))
        assertFalse(error.message.contains(longSecret))
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
    fun `deployMasterDns script captures docker build exit code without bare pipe`() {
        assertFalse(
            MasterDnsDockerScripts.deployMasterDns.contains("| tail") &&
                !MasterDnsDockerScripts.deployMasterDns.contains("PIPESTATUS"),
            "deployMasterDns must use PIPESTATUS to capture docker build exit code — bare pipe loses it",
        )
        assertTrue(MasterDnsDockerScripts.deployMasterDns.contains("build_rc=\$"))
        assertTrue(MasterDnsDockerScripts.deployMasterDns.contains("head -40"))
        assertTrue(MasterDnsDockerScripts.deployMasterDns.contains("tail -80"))
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
        assertTrue(creds.password.all { it == '\u0000' }, "password must be wiped after connect failure")
    }

    @Test
    fun `undeploy clears credentials password when connect fails`() = runTest {
        transport.connectShouldFail = true
        val creds = credentials("super_secret_p@ssw0rd")
        deployer.undeploy(creds).toList()
        assertTrue(creds.password.all { it == '\u0000' }, "password must be wiped after connect failure")
    }

    @Test
    fun `private deploy mappers cover malformed and fallback marker branches`() {
        val parsePortBusy = deployerMethod("parsePortBusy", String::class.java)
        val parseConflict = deployerMethod("parseAmneziaDnsConflict", String::class.java)
        val markerValue = deployerMethod("markerValue", String::class.java, String::class.java)
        val mapSudo = deployerMethod("mapSudoResult", String::class.java)
        val mapBuild = deployerMethod("mapBuildError", String::class.java)
        val mapRun = deployerMethod("mapRunError", String::class.java)
        val mapPort = deployerMethod("mapPortResult", String::class.java)
        val buildToml = deployerMethod("buildClientToml", String::class.java, String::class.java)

        assertEquals(null, parsePortBusy.invoke(null, "ok"))
        assertEquals(null, parsePortBusy.invoke(null, "PORT_BUSY|proto=udp|addr=0.0.0.0|owner="))
        val busy = parsePortBusy.invoke(null, "noise\nPORT_BUSY|proto=UDP|addr=0.0.0.0:53|name=dnsmasq")
            as MasterDnsDeployState.PortBusy
        assertEquals("udp", busy.protocol)
        assertEquals("dnsmasq", busy.owner)

        assertEquals(null, parseConflict.invoke(null, "no conflict"))
        val conflict = parseConflict.invoke(null, "AMNEZIA_DNS_CONFLICT|proto=tcp|addr=127.0.0.1:53")
            as MasterDnsDeployState.AmneziaDnsConflict
        assertEquals("tcp", conflict.protocol)
        assertEquals("127.0.0.1:53", conflict.address)
        assertEquals("value", markerValue.invoke(null, "a|key= value |b", "key"))
        assertEquals("", markerValue.invoke(null, "a|other=value", "key"))

        assertEquals(null, mapSudo.invoke(null, "SUDO_OK"))
        assertEquals("build_failed", mapBuild.invoke(null, "ERR_BUILD"))
        assertEquals("build_failed/bin_missing", mapBuild.invoke(null, "reason=bin_missing"))
        assertEquals("run_failed", mapRun.invoke(null, "ERR_RUN"))
        assertEquals("run_failed|exit=1", mapRun.invoke(null, "ERR_RUN|exit=1"))
        assertEquals(null, mapPort.invoke(null, "PORT_FREE"))
        assertEquals("port_53_busy", mapPort.invoke(null, "PORT_BUSY"))
        assertEquals("port_53_busy|proto=udp", mapPort.invoke(null, "PORT_BUSY|proto=udp"))
        val toml = buildToml.invoke(null, "203.0.113.10", "secret-key") as String
        assertTrue(toml.contains("SERVER = \"203.0.113.10\""))
        assertTrue(toml.contains("ENCRYPTION_KEY = \"secret-key\""))
    }

    private fun deployerMethod(name: String, vararg types: Class<*>) =
        Class.forName("ru.ozero.enginemasterdns.deploy.MasterDnsDeployerImplKt")
            .getDeclaredMethod(name, *types)
            .apply { isAccessible = true }
}
