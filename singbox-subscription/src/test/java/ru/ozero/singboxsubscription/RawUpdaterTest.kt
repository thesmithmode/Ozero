package ru.ozero.singboxsubscription

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ru.ozero.singboxfmt.ShadowsocksBean
import ru.ozero.singboxfmt.TrojanBean
import ru.ozero.singboxfmt.VLESSBean
import ru.ozero.singboxfmt.VMessBean
import ru.ozero.singboxroom.entity.SubscriptionGroup
import java.util.Base64
import javax.net.ssl.SSLHandshakeException
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RawUpdaterTest {

    private val server = MockWebServer()
    private lateinit var groupDao: FakeSubscriptionGroupDao
    private lateinit var profileDao: FakeProxyProfileDao
    private lateinit var rawUpdater: RawUpdater

    private val vless1 =
        "vless://aaaaaaaa-1111-1111-1111-aaaaaaaaaaaa@s1.example.com:443?type=tcp&security=none#S1"
    private val vless2 =
        "vless://bbbbbbbb-2222-2222-2222-bbbbbbbbbbbb@s2.example.com:443?type=tcp&security=none#S2"

    @BeforeEach
    fun setUp() {
        server.start()
        groupDao = FakeSubscriptionGroupDao()
        profileDao = FakeProxyProfileDao()
        rawUpdater = RawUpdater(
            okHttpClient = OkHttpClient(),
            groupDao = groupDao,
            profileDao = profileDao,
        )
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    private fun group(id: Long = 1L): SubscriptionGroup {
        val g = SubscriptionGroup(id = id, name = "Test", subscriptionUrl = server.url("/sub").toString())
        groupDao.groups.add(g)
        return g
    }

    @Test
    fun `should fetch raw links and insert profiles`() = runBlocking {
        server.enqueue(MockResponse().setBody("$vless1\n$vless2"))
        val g = group()

        val result = rawUpdater.refresh(g)

        assertTrue(result.isSuccess)
        assertEquals(2, result.getOrNull())
        assertEquals(2, profileDao.profiles.size)
        assertTrue(profileDao.profiles.all { it.groupId == g.id })
    }

    @Test
    fun `should fetch clash yaml and insert profiles`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """
                proxies:
                  - name: Clash VLESS
                    type: vless
                    server: vless.example.com
                    port: 443
                    uuid: cccccccc-3333-3333-3333-cccccccccccc
                    network: ws
                    tls: true
                    servername: sni.example.com
                    ws-opts:
                      path: /ws
                      headers:
                        Host: host.example.com
                  - name: Clash VMess
                    type: vmess
                    server: vmess.example.com
                    port: 8443
                    uuid: dddddddd-4444-4444-4444-dddddddddddd
                    alterId: 0
                    cipher: auto
                """.trimIndent(),
            ),
        )
        val g = group()

        val result = rawUpdater.refresh(g)

        assertTrue(result.isSuccess)
        assertEquals(2, result.getOrNull())
        assertEquals(listOf("Clash VLESS", "Clash VMess"), profileDao.profiles.map { it.name })
    }

    @Test
    fun `should fetch base64 bundle and insert profiles`() = runBlocking {
        val encoded = Base64.getEncoder()
            .withoutPadding()
            .encodeToString("$vless1\n$vless2".toByteArray())
        server.enqueue(MockResponse().setBody(encoded))
        val g = group()

        val result = rawUpdater.refresh(g)

        assertTrue(result.isSuccess)
        assertEquals(2, result.getOrNull())
    }

    @Test
    fun `should replace existing profiles on refresh`() = runBlocking {
        server.enqueue(MockResponse().setBody(vless1))
        val g = group()
        profileDao.profiles.add(
            ru.ozero.singboxroom.entity.ProxyProfile(
                id = 99L, groupId = g.id, name = "Old",
                beanBlob = byteArrayOf(1, 2), protocolType = 0,
            ),
        )

        rawUpdater.refresh(g)

        assertEquals(1, profileDao.profiles.size)
        assertTrue(profileDao.profiles.none { it.id == 99L })
    }

    @Test
    fun `should parse Subscription-Userinfo header and update group metadata`() = runBlocking {
        server.enqueue(
            MockResponse()
                .addHeader("Subscription-Userinfo", "upload=100; download=200; total=1000; expire=1800000000")
                .setBody(vless1),
        )
        val g = group()

        rawUpdater.refresh(g)

        val updated = groupDao.groups.first { it.id == g.id }
        assertEquals(1800000000L, updated.expiryDate)
        assertEquals(700L, updated.bytesRemaining)
    }

    @Test
    fun `should clamp negative remaining bytes from Subscription-Userinfo`() = runBlocking {
        server.enqueue(
            MockResponse()
                .addHeader("Subscription-Userinfo", "upload=700; download=500; total=1000; expire=1800000000")
                .setBody(vless1),
        )
        val g = group()

        rawUpdater.refresh(g)

        val updated = groupDao.groups.first { it.id == g.id }
        assertEquals(1200L, updated.bytesUsed)
        assertEquals(0L, updated.bytesRemaining)
    }

    @Test
    fun `should preserve group traffic metadata when Subscription-Userinfo is absent`() = runBlocking {
        server.enqueue(MockResponse().setBody(vless1))
        val g = group().copy(bytesUsed = 11L, bytesRemaining = 22L, expiryDate = 33L)
        groupDao.groups.clear()
        groupDao.groups.add(g)

        rawUpdater.refresh(g)

        val updated = groupDao.groups.first { it.id == g.id }
        assertEquals(11L, updated.bytesUsed)
        assertEquals(22L, updated.bytesRemaining)
        assertEquals(33L, updated.expiryDate)
    }

    @Test
    fun `should use fallback server names when imported bean has blank name`() = runBlocking {
        val unnamed =
            "vless://aaaaaaaa-1111-1111-1111-aaaaaaaaaaaa@s1.example.com:443?type=tcp&security=none"
        server.enqueue(MockResponse().setBody("$unnamed\n$unnamed"))
        val g = group()

        rawUpdater.refresh(g)

        assertEquals(listOf("Server 1", "Server 2"), profileDao.profiles.map { it.name })
    }

    @Test
    fun `protocolTypeOf maps all supported bean classes and defaults unknown to VLESS`() {
        assertEquals(RawUpdater.PROTOCOL_VLESS, RawUpdater.protocolTypeOf(VLESSBean()))
        assertEquals(RawUpdater.PROTOCOL_VMESS, RawUpdater.protocolTypeOf(VMessBean()))
        assertEquals(RawUpdater.PROTOCOL_TROJAN, RawUpdater.protocolTypeOf(TrojanBean()))
        assertEquals(RawUpdater.PROTOCOL_SHADOWSOCKS, RawUpdater.protocolTypeOf(ShadowsocksBean()))
        assertEquals(
            RawUpdater.PROTOCOL_VLESS,
            RawUpdater.protocolTypeOf(object : ru.ozero.singboxfmt.AbstractBean() {}),
        )
    }

    @Test
    fun `should update lastUpdated timestamp after successful refresh`() = runBlocking {
        val before = System.currentTimeMillis()
        server.enqueue(MockResponse().setBody(vless1))
        val g = group()

        rawUpdater.refresh(g)

        val updated = groupDao.groups.first { it.id == g.id }
        assertTrue(updated.lastUpdated >= before)
    }

    @Test
    fun `should return failure on network error`() = runBlocking {
        server.shutdown()

        val g = SubscriptionGroup(id = 1L, name = "Test", subscriptionUrl = "http://127.0.0.1:1/sub")
        groupDao.groups.add(g)

        val result = rawUpdater.refresh(g)

        assertTrue(result.isFailure)
    }

    @Test
    fun `should normalize TLS chain validation failures`() = runBlocking {
        rawUpdater = RawUpdater(
            okHttpClient = OkHttpClient.Builder()
                .addInterceptor { throw SSLHandshakeException("Chain validation failed: bad cert") }
                .build(),
            groupDao = groupDao,
            profileDao = profileDao,
        )
        val g = group()

        val result = rawUpdater.refresh(g)

        assertTrue(result.isFailure)
        assertEquals(
            "Subscription TLS certificate chain validation failed",
            result.exceptionOrNull()?.message,
        )
    }

    @Test
    fun `should return zero count for empty body`() = runBlocking {
        server.enqueue(MockResponse().setBody(""))
        val g = group()

        val result = rawUpdater.refresh(g)

        assertTrue(result.isSuccess)
        assertEquals(0, result.getOrNull())
        assertEquals(0, profileDao.profiles.size)
    }

    @Test
    fun `should skip comment lines in raw body`() = runBlocking {
        server.enqueue(MockResponse().setBody("# header comment\n$vless1\n# another comment\n$vless2"))
        val g = group()

        rawUpdater.refresh(g)

        assertEquals(2, profileDao.profiles.size)
    }

    @Test
    fun `should set sequential userOrder on inserted profiles`() = runBlocking {
        server.enqueue(MockResponse().setBody("$vless1\n$vless2"))
        val g = group()

        rawUpdater.refresh(g)

        val orders = profileDao.profiles.sortedBy { it.userOrder }.map { it.userOrder }
        assertEquals(listOf(0, 1), orders)
    }

    @Test
    fun `should preserve profile ids across refresh by userOrder`() = runBlocking {
        server.enqueue(MockResponse().setBody("$vless1\n$vless2"))
        val g = group()

        rawUpdater.refresh(g)
        val firstIds = profileDao.profiles.sortedBy { it.userOrder }.map { it.id }

        server.enqueue(MockResponse().setBody("$vless1\n$vless2"))
        rawUpdater.refresh(g)
        val secondIds = profileDao.profiles.sortedBy { it.userOrder }.map { it.id }

        assertEquals(firstIds, secondIds)
    }

    @Test
    fun `should preserve profile ids across refresh when server order changes`() = runBlocking {
        server.enqueue(MockResponse().setBody("$vless1\n$vless2"))
        val g = group()

        rawUpdater.refresh(g)
        val firstIds = profileDao.profiles.sortedBy { it.name }.map { it.id }

        server.enqueue(MockResponse().setBody("$vless2\n$vless1"))
        rawUpdater.refresh(g)
        val secondIds = profileDao.profiles.sortedBy { it.name }.map { it.id }

        assertEquals(firstIds, secondIds)
    }
}
