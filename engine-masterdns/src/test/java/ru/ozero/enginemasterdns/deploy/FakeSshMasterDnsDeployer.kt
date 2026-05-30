package ru.ozero.enginemasterdns.deploy

import kotlinx.coroutines.flow.Flow

class FakeSshMasterDnsDeployer(private val transport: FakeSshTransport) : MasterDnsServerDeployer {
    override fun deploy(credentials: MasterDnsDeployCredentials): Flow<MasterDnsDeployState> =
        MasterDnsDeployerImpl(transport).deploy(credentials)

    override fun undeploy(credentials: MasterDnsDeployCredentials): Flow<MasterDnsDeployState> =
        MasterDnsDeployerImpl(transport).undeploy(credentials)

    override fun removeAmneziaDnsAndContinue(credentials: MasterDnsDeployCredentials): Flow<MasterDnsDeployState> =
        MasterDnsDeployerImpl(transport).removeAmneziaDnsAndContinue(credentials)
}
