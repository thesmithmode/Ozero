package ru.ozero.enginemasterdns.deploy

import kotlin.test.assertNotNull
import org.junit.jupiter.api.Test

class SshjMasterDnsDeployerTest {

    @Test
    fun `methods create cold deployment flows without connecting immediately`() {
        val deployer = SshjMasterDnsDeployer()
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
