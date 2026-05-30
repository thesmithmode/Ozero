package ru.ozero.app.ui

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import ru.ozero.commonnet.IpInfo
import ru.ozero.commonnet.IpInfoProvider
import ru.ozero.enginescore.ExitNodeStrategy
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ExitNodeResolverTest {

    @Test
    fun `ViaSocks success shows IP from proxy`() = runTest {
        val provider = FakeIpInfoProvider()
        val resolver = ExitNodeResolver(provider, clock = { 42L })

        val state = resolver.resolve(ExitNodeStrategy.ViaSocks("127.0.0.1", 1080))

        val loaded = assertIs<IpInfoState.Loaded>(state)
        assertEquals("203.0.113.10", loaded.info.ip)
        assertEquals(1, provider.fetchViaCalls)
        assertEquals(0, provider.fetchCalls)
        assertEquals("127.0.0.1", provider.lastSocksHost)
        assertEquals(1080, provider.lastSocksPort)
    }

    @Test
    fun `ViaSocks failure does not call direct fetch`() = runTest {
        val provider = FakeIpInfoProvider(
            viaResult = Result.failure(java.io.IOException("socks down")),
        )
        val resolver = ExitNodeResolver(provider)

        val state = resolver.resolve(ExitNodeStrategy.ViaSocks("127.0.0.1", 1080))

        val error = assertIs<IpInfoState.Error>(state)
        assertEquals("socks down", error.message)
        assertEquals(1, provider.fetchViaCalls)
        assertEquals(0, provider.fetchCalls)
    }

    @Test
    fun `LocationOnly does not fetch HTTP`() = runTest {
        val provider = FakeIpInfoProvider()
        val resolver = ExitNodeResolver(provider, clock = { 42L })

        val state = resolver.resolve(ExitNodeStrategy.LocationOnly("Germany", "DE"))

        val loaded = assertIs<IpInfoState.Loaded>(state)
        assertEquals("", loaded.info.ip)
        assertEquals("Germany", loaded.info.country)
        assertEquals("DE", loaded.info.countryCode)
        assertEquals(0, provider.fetchCalls + provider.fetchViaCalls)
    }

    @Test
    fun `ProviderLabel does not fetch HTTP`() = runTest {
        val provider = FakeIpInfoProvider()
        val resolver = ExitNodeResolver(provider, clock = { 42L })

        val state = resolver.resolve(ExitNodeStrategy.ProviderLabel("Cloudflare WARP"))

        val loaded = assertIs<IpInfoState.Loaded>(state)
        assertEquals("", loaded.info.ip)
        assertEquals("Cloudflare WARP", loaded.info.country)
        assertEquals(0, provider.fetchCalls + provider.fetchViaCalls)
    }

    @Test
    fun `ProviderLabel can show known static IP without fetch`() = runTest {
        val provider = FakeIpInfoProvider()
        val resolver = ExitNodeResolver(provider, clock = { 42L })

        val state = resolver.resolve(
            ExitNodeStrategy.ProviderLabel(
                label = "Germany",
                ip = "198.51.100.44",
                countryCode = "DE",
            ),
        )

        val loaded = assertIs<IpInfoState.Loaded>(state)
        assertEquals("198.51.100.44", loaded.info.ip)
        assertEquals("Germany", loaded.info.country)
        assertEquals("DE", loaded.info.countryCode)
        assertEquals(0, provider.fetchCalls + provider.fetchViaCalls)
    }

    @Test
    fun `AutoSelected preserves URnetwork auto state`() = runTest {
        val provider = FakeIpInfoProvider()
        val resolver = ExitNodeResolver(provider)

        val state = resolver.resolve(ExitNodeStrategy.AutoSelected())

        assertIs<IpInfoState.AutoSelected>(state)
        assertEquals(0, provider.fetchCalls + provider.fetchViaCalls)
    }

    private class FakeIpInfoProvider(
        private val directResult: Result<IpInfo> = Result.success(sampleIp("198.51.100.20")),
        private val viaResult: Result<IpInfo> = Result.success(sampleIp("203.0.113.10")),
    ) : IpInfoProvider {
        var fetchCalls = 0
        var fetchViaCalls = 0
        var lastSocksHost: String? = null
        var lastSocksPort: Int? = null

        override suspend fun fetch(): Result<IpInfo> {
            fetchCalls++
            return directResult
        }

        override suspend fun fetchVia(socksHost: String?, socksPort: Int?): Result<IpInfo> {
            fetchViaCalls++
            lastSocksHost = socksHost
            lastSocksPort = socksPort
            return viaResult
        }
    }

    private companion object {
        fun sampleIp(ip: String) = IpInfo(
            ip = ip,
            country = "Germany",
            countryCode = "DE",
            city = "Berlin",
            fetchedAtMs = 1L,
        )
    }
}
