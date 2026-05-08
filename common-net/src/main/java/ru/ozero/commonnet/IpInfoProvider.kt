package ru.ozero.commonnet

interface IpInfoProvider {
    suspend fun fetch(): Result<IpInfo>
}
