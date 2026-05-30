package ru.ozero.enginemasterdns.deploy

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

interface MasterDnsServerDeployer {
    fun deploy(credentials: MasterDnsDeployCredentials): Flow<MasterDnsDeployState>
    fun undeploy(credentials: MasterDnsDeployCredentials): Flow<MasterDnsDeployState>
    fun removeAmneziaDnsAndContinue(credentials: MasterDnsDeployCredentials): Flow<MasterDnsDeployState> =
        flowOf(MasterDnsDeployState.Error("amnezia_dns_remove_unsupported"))
}
