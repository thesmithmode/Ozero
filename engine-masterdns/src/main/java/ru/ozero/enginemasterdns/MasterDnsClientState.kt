package ru.ozero.enginemasterdns

sealed class MasterDnsClientState {
    data object Idle : MasterDnsClientState()
    data object Starting : MasterDnsClientState()
    data class Running(val port: Int) : MasterDnsClientState()
    data class Error(val message: String) : MasterDnsClientState()
}
