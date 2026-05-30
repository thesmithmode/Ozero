package ru.ozero.enginemasterdns.deploy

sealed class MasterDnsDeployState {
    abstract val progressPercent: Int

    data object Idle : MasterDnsDeployState() {
        override val progressPercent: Int = 0
    }

    data object Connecting : MasterDnsDeployState() {
        override val progressPercent: Int = 5
    }

    data object CheckingPreflight : MasterDnsDeployState() {
        override val progressPercent: Int = 15
    }

    data class AmneziaDnsConflict(val protocol: String, val address: String) : MasterDnsDeployState() {
        override val progressPercent: Int = 15
    }

    data object InstallingDocker : MasterDnsDeployState() {
        override val progressPercent: Int = 35
    }

    data object BuildingImage : MasterDnsDeployState() {
        override val progressPercent: Int = 60
    }

    data object StartingContainer : MasterDnsDeployState() {
        override val progressPercent: Int = 80
    }

    data object ExtractingKey : MasterDnsDeployState() {
        override val progressPercent: Int = 92
    }

    data object Removing : MasterDnsDeployState() {
        override val progressPercent: Int = 50
    }

    data object Removed : MasterDnsDeployState() {
        override val progressPercent: Int = 100
    }

    data class Done(val configToml: String) : MasterDnsDeployState() {
        override val progressPercent: Int = 100
    }

    data class Error(val message: String) : MasterDnsDeployState() {
        override val progressPercent: Int = 0
    }
}
