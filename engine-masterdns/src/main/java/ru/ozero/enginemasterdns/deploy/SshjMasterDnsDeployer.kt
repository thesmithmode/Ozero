package ru.ozero.enginemasterdns.deploy

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import java.io.File

class SshjMasterDnsDeployer(
    private val knownHostsFile: File,
) : MasterDnsServerDeployer {

    override fun deploy(credentials: MasterDnsDeployCredentials): Flow<MasterDnsDeployState> =
        MasterDnsDeployerImpl(SshjTransport(knownHostsFile)).deploy(credentials).flowOn(Dispatchers.IO)

    override fun undeploy(credentials: MasterDnsDeployCredentials): Flow<MasterDnsDeployState> =
        MasterDnsDeployerImpl(SshjTransport(knownHostsFile)).undeploy(credentials).flowOn(Dispatchers.IO)
}
