package ru.ozero.commonnet

import javax.net.SocketFactory

interface IpInfoProvider {
    suspend fun fetch(): Result<IpInfo>

    suspend fun fetchVia(socksHost: String?, socksPort: Int?): Result<IpInfo> = fetch()

    suspend fun fetchViaSocketFactory(socketFactory: SocketFactory?): Result<IpInfo> = fetch()
}
