package ru.ozero.enginemasterdns.deploy

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn

class SshjMasterDnsDeployer : MasterDnsServerDeployer {

    override fun deploy(credentials: MasterDnsDeployCredentials): Flow<MasterDnsDeployState> =
        MasterDnsDeployerImpl(SshjTransport()).deploy(credentials).flowOn(Dispatchers.IO)
}
