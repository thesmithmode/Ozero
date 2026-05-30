package ru.ozero.enginemasterdns.deploy

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn

class SshjMasterDnsDeployer : MasterDnsServerDeployer {

    override fun deploy(credentials: MasterDnsDeployCredentials): Flow<MasterDnsDeployState> =
        MasterDnsDeployerImpl(SshjTransport()).deploy(credentials).flowOn(Dispatchers.IO)

    override fun undeploy(credentials: MasterDnsDeployCredentials): Flow<MasterDnsDeployState> =
        MasterDnsDeployerImpl(SshjTransport()).undeploy(credentials).flowOn(Dispatchers.IO)

    override fun removeAmneziaDnsAndContinue(credentials: MasterDnsDeployCredentials): Flow<MasterDnsDeployState> =
        MasterDnsDeployerImpl(SshjTransport()).removeAmneziaDnsAndContinue(credentials).flowOn(Dispatchers.IO)
}
