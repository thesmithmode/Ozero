package ru.ozero.enginemasterdns.deploy

import kotlin.test.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class SshjMasterDnsDeployerTest {

    @Test
    fun `methods create cold deployment flows without connecting immediately`(@TempDir tmp: File) {
        val deployer = SshjMasterDnsDeployer(File(tmp, "known_hosts"))
        val credentials = MasterDnsDeployCredentials(
            host = "192.0.2.10",
            login = "user",
            password = "password".toCharArray(),
        )

        assertNotNull(deployer.deploy(credentials))
        assertNotNull(deployer.undeploy(credentials))
        assertNotNull(deployer.removeAmneziaDnsAndContinue(credentials))
    }
}
