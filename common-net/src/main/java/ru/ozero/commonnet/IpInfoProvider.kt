package ru.ozero.commonnet

interface IpInfoProvider {
    suspend fun fetch(): Result<IpInfo>

    suspend fun fetchVia(socksHost: String?, socksPort: Int?): Result<IpInfo> = fetch()
}
