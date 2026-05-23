package ru.ozero.enginemasterdns.deploy

sealed class MasterDnsDeployState {
    data object Idle : MasterDnsDeployState()
    data object Connecting : MasterDnsDeployState()
    data object CheckingPreflight : MasterDnsDeployState()
    data object InstallingDocker : MasterDnsDeployState()
    data object BuildingImage : MasterDnsDeployState()
    data object StartingContainer : MasterDnsDeployState()
    data object ExtractingKey : MasterDnsDeployState()
    data class Done(val configToml: String) : MasterDnsDeployState()
    data class Error(val message: String) : MasterDnsDeployState()
}
