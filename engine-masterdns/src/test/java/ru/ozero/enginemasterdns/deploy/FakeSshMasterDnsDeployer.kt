package ru.ozero.enginemasterdns.deploy

import kotlinx.coroutines.flow.Flow

class FakeSshMasterDnsDeployer(private val transport: FakeSshTransport) : MasterDnsServerDeployer {
    override fun deploy(credentials: MasterDnsDeployCredentials): Flow<MasterDnsDeployState> =
        MasterDnsDeployerImpl(transport).deploy(credentials)
}
