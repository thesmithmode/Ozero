package ru.ozero.commonnet

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.IOException
import java.util.concurrent.CancellationException
import java.util.concurrent.TimeUnit
import javax.net.SocketFactory
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OkHttpIpInfoProviderTest {
    private lateinit var server: MockWebServer
    private lateinit var provider: OkHttpIpInfoProvider

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        val client = OkHttpClient.Builder()
            .connectTimeout(2, TimeUnit.SECONDS)
            .readTimeout(2, TimeUnit.SECONDS)
            .build()
        provider = OkHttpIpInfoProvider(
            client = client,
            endpoint = server.url("/json/").toString(),
            clock = { 1_700_000_000_000L },
        )
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun parsesFullJson() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"ip":"203.0.113.1","country_name":"Germany","country_code":"DE","city":"Berlin"}""",
            ),
        )
        val info = provider.fetch().getOrThrow()
        assertEquals("203.0.113.1", info.ip)
        assertEquals("Germany", info.country)
        assertEquals("DE", info.countryCode)
        assertEquals("Berlin", info.city)
        assertEquals(1_700_000_000_000L, info.fetchedAtMs)
    }

    @Test
    fun parsesIpOnly() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"ip":"1.1.1.1"}"""))
        val info = provider.fetch().getOrThrow()
        assertEquals("1.1.1.1", info.ip)
        assertNull(info.country)
        assertNull(info.countryCode)
        assertNull(info.city)
    }

    @Test
    fun ignoresEmptyCountry() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"ip":"8.8.8.8","country_name":"","country_code":""}""",
            ),
        )
        val info = provider.fetch().getOrThrow()
        assertNull(info.country)
        assertNull(info.countryCode)
    }

    @Test
    fun failsOn500() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))
        val result = provider.fetch()
        assertTrue(result.isFailure)
        assertIs<IOException>(result.exceptionOrNull())
    }

    @Test
    fun failsOn404() = runTest {
        server.enqueue(MockResponse().setResponseCode(404))
        val result = provider.fetch()
        assertTrue(result.isFailure)
    }

    @Test
    fun failsOnEmptyBody() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(""))
        val result = provider.fetch()
        assertTrue(result.isFailure)
        assertIs<IOException>(result.exceptionOrNull())
    }

    @Test
    fun failsOnMissingIp() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody("""{"country_name":"X"}"""),
        )
        val result = provider.fetch()
        assertTrue(result.isFailure)
        assertIs<IOException>(result.exceptionOrNull())
    }

    @Test
    fun failsOnMalformedJson() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("not-json-at-all"))
        val result = provider.fetch()
        assertTrue(result.isFailure)
    }

    @Test
    fun failsOnConnectionDrop() = runTest {
        server.enqueue(
            MockResponse().setSocketPolicy(okhttp3.mockwebserver.SocketPolicy.DISCONNECT_AT_START),
        )
        val result = provider.fetch()
        assertTrue(result.isFailure)
    }

    @Test
    fun parseHelperHandlesUnicodeCity() {
        val info = parse(
            """{"ip":"1.2.3.4","city":"München","country_name":"Germany"}""",
            42L,
        )
        assertEquals("München", info.city)
        assertEquals(42L, info.fetchedAtMs)
    }

    @Test
    fun defaultConstructorBuildsClient() {
        OkHttpIpInfoProvider()
    }

    @Test
    fun sendsExpectedHeaders() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"ip":"1.2.3.4"}"""))
        provider.fetch().getOrThrow()
        val recorded = server.takeRequest()
        assertEquals("Ozero-IpInfo/2", recorded.getHeader("User-Agent"))
        assertEquals("application/json", recorded.getHeader("Accept"))
        assertEquals("GET", recorded.method)
    }

    @Test
    fun fetchViaNullDelegatesToDefaultClient() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"ip":"1.1.1.1"}"""))
        val info = provider.fetchVia(socksHost = null, socksPort = null).getOrThrow()
        assertEquals("1.1.1.1", info.ip)
    }

    @Test
    fun fetchViaInvalidPortDelegatesToDefaultClient() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"ip":"2.2.2.2"}"""))
        val info = provider.fetchVia(socksHost = "127.0.0.1", socksPort = 0).getOrThrow()
        assertEquals("2.2.2.2", info.ip)
    }

    @Test
    fun fetchViaSocketFactoryNullUsesDefaultClient() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"ip":"3.3.3.3"}"""))
        val info = provider.fetchViaSocketFactory(null).getOrThrow()
        assertEquals("3.3.3.3", info.ip)
    }

    @Test
    fun fetchViaSocketFactoryNonNullUsesProvidedFactory() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"ip":"4.4.4.4"}"""))
        val factory = SocketFactory.getDefault()
        val info = provider.fetchViaSocketFactory(factory).getOrThrow()
        assertEquals("4.4.4.4", info.ip)
    }

    @Test
    fun parseFallsBackToLegacyCountryFields() {
        val info = parse(
            """{"ip":"1.2.3.4","country":"Germany","country_iso":"DE"}""",
            99L,
        )
        assertEquals("Germany", info.country)
        assertEquals("DE", info.countryCode)
    }

    @Test
    fun fetchPropagatesCancellationException() = runTest {
        val throwingClient = OkHttpClient.Builder()
            .addInterceptor { throw CancellationException("cancelled") }
            .build()
        val throwingProvider = OkHttpIpInfoProvider(
            client = throwingClient,
            endpoint = server.url("/json/").toString(),
            clock = { 1L },
        )

        assertTrue(kotlin.runCatching { throwingProvider.fetch() }.exceptionOrNull() is CancellationException)
    }

    @Test
    fun fetchViaWithSocksProxyToDeadPortFails() = runTest {
        val deadSocksPort = MockWebServer().run {
            start()
            val p = port
            shutdown()
            p
        }
        val result = provider.fetchVia(socksHost = "127.0.0.1", socksPort = deadSocksPort)
        assertTrue(
            result.isFailure,
            "SOCKS proxy на закрытый порт обязан fail — fetchVia с proxy не должен фоллбечиться " +
                "на direct connect (это IP-leak). result=$result",
        )
    }
}
