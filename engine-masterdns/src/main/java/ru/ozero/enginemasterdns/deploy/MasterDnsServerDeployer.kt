package ru.ozero.enginemasterdns.deploy

import kotlinx.coroutines.flow.Flow

interface MasterDnsServerDeployer {
    fun deploy(credentials: MasterDnsDeployCredentials): Flow<MasterDnsDeployState>
}
