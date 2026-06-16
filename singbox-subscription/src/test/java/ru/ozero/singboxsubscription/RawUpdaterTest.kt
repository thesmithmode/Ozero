@file:Suppress("LargeClass")

package ru.ozero.singboxsubscription

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
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
import ru.ozero.singboxsubscription.parser.RawShareLinksParser
import java.util.Base64
import javax.net.ssl.SSLHandshakeException
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
    private val vless1RotatedRuntime =
        "vless://aaaaaaaa-1111-1111-1111-aaaaaaaaaaaa@s1.example.com:443?type=grpc&security=none#S1"
    private val trojan1 =
        "trojan://secret-one@tr.example.com:443?security=tls&sni=tr.example.com#Trojan1"
    private val trojan2 =
        "trojan://secret-two@tr.example.com:443?security=tls&sni=tr.example.com#Trojan2"
    private val shadowsocks1 =
        "ss://YWVzLTEyOC1nY206cGFzcy1vbmU@ss.example.com:8388#SS1"
    private val shadowsocks2 =
        "ss://YWVzLTEyOC1nY206cGFzcy10d28@ss.example.com:8388#SS2"

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
    fun `should return failure on unsuccessful http response`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(503).setBody("temporarily unavailable"))
        val g = group()

        val result = rawUpdater.refresh(g)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("Subscription HTTP 503"))
        assertEquals(0, profileDao.profiles.size)
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
    fun `should preserve non chain TLS failures`() = runBlocking {
        rawUpdater = RawUpdater(
            okHttpClient = OkHttpClient.Builder()
                .addInterceptor { throw SSLHandshakeException("certificate expired") }
                .build(),
            groupDao = groupDao,
            profileDao = profileDao,
        )
        val g = group()

        val result = rawUpdater.refresh(g)

        assertTrue(result.isFailure)
        assertEquals("certificate expired", result.exceptionOrNull()?.message)
    }

    @Test
    fun `should return zero count for empty body`() = runBlocking {
        server.enqueue(MockResponse().setBody(""))
        val g = group()

        val result = rawUpdater.refresh(g)

        assertEquals(true, result.isSuccess, "Expected success but got ${result.exceptionOrNull()}")
        assertEquals(0, result.getOrNull())
        assertEquals(0, profileDao.profiles.size)
    }

    @Test
    fun `should treat bodyless successful response as empty subscription`() = runBlocking {
        rawUpdater = RawUpdater(
            okHttpClient = OkHttpClient.Builder()
                .addInterceptor { chain ->
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(204)
                        .message("No Content")
                        .body("".toResponseBody())
                        .build()
                }
                .build(),
            groupDao = groupDao,
            profileDao = profileDao,
        )
        val g = group()

        val result = rawUpdater.refresh(g)

        assertTrue(result.isSuccess, "Expected success but got ${result.exceptionOrNull()}")
        assertEquals(0, result.getOrNull())
        assertEquals(0, profileDao.profiles.size)
    }

    @Test
    fun `should treat null body successful response as empty subscription`() = runBlocking {
        val mockedResponse = mockk<Response>(relaxed = true).apply {
            every { body } returns null
            every { isSuccessful } returns true
            every { header("Subscription-Userinfo") } returns null
            every { code } returns 200
        }
        val mockedCall = mockk<Call>(relaxed = true)
        every { mockedCall.execute() } returns mockedResponse

        val mockedClient = mockk<OkHttpClient>()
        every { mockedClient.newCall(any()) } returns mockedCall
        rawUpdater = RawUpdater(
            okHttpClient = mockedClient,
            groupDao = groupDao,
            profileDao = profileDao,
        )

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

    @Test
    fun `should preserve profile ids when runtime fields changed on refresh`() = runBlocking {
        server.enqueue(MockResponse().setBody(vless1))
        val g = group()

        rawUpdater.refresh(g)
        val firstProfile = profileDao.profiles.first()
        val firstId = firstProfile.id

        server.enqueue(MockResponse().setBody(vless1RotatedRuntime))
        rawUpdater.refresh(g)
        val secondId = profileDao.profiles.first().id

        assertEquals(firstId, secondId)
        assertTrue(!firstProfile.beanBlob.contentEquals(profileDao.profiles.first().beanBlob))
    }

    @Test
    fun `should preserve profile id when subscription renames same server`() = runBlocking {
        val renamed =
            "vless://aaaaaaaa-1111-1111-1111-aaaaaaaaaaaa@s1.example.com:443?type=tcp&security=none#Renamed"
        server.enqueue(MockResponse().setBody(vless1))
        val g = group()

        rawUpdater.refresh(g)
        val first = profileDao.profiles.single()

        server.enqueue(MockResponse().setBody(renamed))
        rawUpdater.refresh(g)
        val second = profileDao.profiles.single()

        assertEquals(first.id, second.id)
        assertEquals("Renamed", second.name)
    }

    @Test
    fun `should not reuse the same existing id for duplicate stable matches`() = runBlocking {
        val duplicate1 =
            "vless://aaaaaaaa-1111-1111-1111-aaaaaaaaaaaa@dup.example.com:443?type=tcp&security=none#Dup"
        val duplicate2 =
            "vless://bbbbbbbb-2222-2222-2222-bbbbbbbbbbbb@dup.example.com:443?type=tcp&security=none#Dup"
        server.enqueue(MockResponse().setBody("$duplicate1\n$duplicate2"))
        val g = group()

        profileDao.profiles.add(
            ru.ozero.singboxroom.entity.ProxyProfile(
                id = 77L,
                groupId = g.id,
                name = "Dup",
                beanBlob = ru.ozero.singboxfmt.KryoSerializer.serialize(
                    RawShareLinksParser.parse(duplicate1).single(),
                ),
                protocolType = RawUpdater.PROTOCOL_VLESS,
            ),
        )

        rawUpdater.refresh(g)

        val ids = profileDao.profiles.map { it.id }
        assertEquals(2, profileDao.profiles.size)
        assertEquals(2, ids.toSet().size)
        assertTrue(profileDao.profiles.any { it.id == 77L })
    }

    @Test
    fun `should preserve duplicate server ids by credentials when provider reorders rows`() = runBlocking {
        val duplicateA =
            "vless://aaaaaaaa-1111-1111-1111-aaaaaaaaaaaa@dup.example.com:443?type=tcp&security=none#First"
        val duplicateB =
            "vless://bbbbbbbb-2222-2222-2222-bbbbbbbbbbbb@dup.example.com:443?type=tcp&security=none#Second"
        server.enqueue(MockResponse().setBody("$duplicateA\n$duplicateB"))
        val g = group()

        rawUpdater.refresh(g)
        val firstByName = profileDao.profiles.associateBy { it.name }
        val firstAId = firstByName.getValue("First").id
        val firstBId = firstByName.getValue("Second").id

        server.enqueue(MockResponse().setBody("$duplicateB\n$duplicateA"))
        rawUpdater.refresh(g)
        val secondByName = profileDao.profiles.associateBy { it.name }

        assertEquals(firstAId, secondByName.getValue("First").id)
        assertEquals(firstBId, secondByName.getValue("Second").id)
    }

    @Test
    fun `should preserve duplicate server ids by transport when provider reorders rows`() = runBlocking {
        val ws =
            "vless://aaaaaaaa-1111-1111-1111-aaaaaaaaaaaa@dup.example.com:443" +
                "?type=ws&security=tls&sni=ws.example.com&host=front.example.com&path=/ws#WS"
        val grpc =
            "vless://aaaaaaaa-1111-1111-1111-aaaaaaaaaaaa@dup.example.com:443" +
                "?type=grpc&security=tls&sni=grpc.example.com&serviceName=grpc-svc#GRPC"
        server.enqueue(MockResponse().setBody("$ws\n$grpc"))
        val g = group()

        rawUpdater.refresh(g)
        val firstByName = profileDao.profiles.associateBy { it.name }
        val wsId = firstByName.getValue("WS").id
        val grpcId = firstByName.getValue("GRPC").id

        server.enqueue(MockResponse().setBody("$grpc\n$ws"))
        rawUpdater.refresh(g)
        val secondByName = profileDao.profiles.associateBy { it.name }

        assertEquals(wsId, secondByName.getValue("WS").id)
        assertEquals(grpcId, secondByName.getValue("GRPC").id)
    }

    @Test
    fun `should preserve duplicate VLESS ids by flow when provider reorders rows`() = runBlocking {
        val vision =
            "vless://aaaaaaaa-1111-1111-1111-aaaaaaaaaaaa@dup.example.com:443" +
                "?type=tcp&security=reality&flow=xtls-rprx-vision&pbk=pub&sid=01#Vision"
        val blankFlow =
            "vless://aaaaaaaa-1111-1111-1111-aaaaaaaaaaaa@dup.example.com:443" +
                "?type=tcp&security=reality&pbk=pub&sid=01#Blank"
        server.enqueue(MockResponse().setBody("$vision\n$blankFlow"))
        val g = group()

        rawUpdater.refresh(g)
        val firstByName = profileDao.profiles.associateBy { it.name }
        val visionId = firstByName.getValue("Vision").id
        val blankId = firstByName.getValue("Blank").id

        server.enqueue(MockResponse().setBody("$blankFlow\n$vision"))
        rawUpdater.refresh(g)
        val secondByName = profileDao.profiles.associateBy { it.name }

        assertEquals(visionId, secondByName.getValue("Vision").id)
        assertEquals(blankId, secondByName.getValue("Blank").id)
    }

    @Test
    fun `should preserve duplicate VMess ids by alter id and encryption when provider reorders rows`() = runBlocking {
        val firstOrder =
            """
            {
              "outbounds": [
                {
                  "type": "vmess",
                  "tag": "VMess Auto",
                  "server": "vmess-dup.example.com",
                  "server_port": 443,
                  "uuid": "cccccccc-3333-3333-3333-cccccccccccc",
                  "alter_id": 0,
                  "security": "auto"
                },
                {
                  "type": "vmess",
                  "tag": "VMess AES",
                  "server": "vmess-dup.example.com",
                  "server_port": 443,
                  "uuid": "cccccccc-3333-3333-3333-cccccccccccc",
                  "alter_id": 4,
                  "security": "aes-128-gcm"
                }
              ]
            }
            """.trimIndent()
        val secondOrder =
            """
            {
              "outbounds": [
                {
                  "type": "vmess",
                  "tag": "VMess AES",
                  "server": "vmess-dup.example.com",
                  "server_port": 443,
                  "uuid": "cccccccc-3333-3333-3333-cccccccccccc",
                  "alter_id": 4,
                  "security": "aes-128-gcm"
                },
                {
                  "type": "vmess",
                  "tag": "VMess Auto",
                  "server": "vmess-dup.example.com",
                  "server_port": 443,
                  "uuid": "cccccccc-3333-3333-3333-cccccccccccc",
                  "alter_id": 0,
                  "security": "auto"
                }
              ]
            }
            """.trimIndent()
        server.enqueue(MockResponse().setBody(firstOrder))
        val g = group()

        rawUpdater.refresh(g)
        val firstByName = profileDao.profiles.associateBy { it.name }
        val autoId = firstByName.getValue("VMess Auto").id
        val aesId = firstByName.getValue("VMess AES").id

        server.enqueue(MockResponse().setBody(secondOrder))
        rawUpdater.refresh(g)
        val secondByName = profileDao.profiles.associateBy { it.name }

        assertEquals(autoId, secondByName.getValue("VMess Auto").id)
        assertEquals(aesId, secondByName.getValue("VMess AES").id)
    }

    @Test
    fun `should preserve duplicate VLESS ids by TLS and websocket runtime fields when provider reorders rows`() =
        runBlocking {
            val insecure =
                "vless://aaaaaaaa-1111-1111-1111-aaaaaaaaaaaa@dup.example.com:443" +
                    "?type=ws&security=tls&sni=dup.example.com&host=front.example.com" +
                    "&path=/ws&allowInsecure=1&ed=16&eh=Sec-WebSocket-Protocol#Insecure"
            val strict =
                "vless://aaaaaaaa-1111-1111-1111-aaaaaaaaaaaa@dup.example.com:443" +
                    "?type=ws&security=tls&sni=dup.example.com&host=front.example.com" +
                    "&path=/ws&allowInsecure=0&ed=0&eh=Early#Strict"
            server.enqueue(MockResponse().setBody("$insecure\n$strict"))
            val g = group()

            rawUpdater.refresh(g)
            val firstByName = profileDao.profiles.associateBy { it.name }
            val insecureId = firstByName.getValue("Insecure").id
            val strictId = firstByName.getValue("Strict").id

            server.enqueue(MockResponse().setBody("$strict\n$insecure"))
            rawUpdater.refresh(g)
            val secondByName = profileDao.profiles.associateBy { it.name }

            assertEquals(insecureId, secondByName.getValue("Insecure").id)
            assertEquals(strictId, secondByName.getValue("Strict").id)
        }

    @Test
    fun `should use full identity when existing profiles share same base key`() = runBlocking {
        val ws =
            "vless://aaaaaaaa-1111-1111-1111-aaaaaaaaaaaa@dup-existing.example.com:443" +
                "?type=ws&security=tls&sni=dup.example.com&host=front.example.com&path=/ws#WS"
        val grpc =
            "vless://aaaaaaaa-1111-1111-1111-aaaaaaaaaaaa@dup-existing.example.com:443" +
                "?type=grpc&security=tls&sni=dup.example.com&serviceName=grpc-svc#GRPC"
        server.enqueue(MockResponse().setBody("$ws\n$grpc"))
        val g = group()

        rawUpdater.refresh(g)
        val firstByName = profileDao.profiles.associateBy { it.name }
        val grpcId = firstByName.getValue("GRPC").id

        server.enqueue(MockResponse().setBody(grpc))
        rawUpdater.refresh(g)

        val onlyProfile = profileDao.profiles.single()
        assertEquals("GRPC", onlyProfile.name)
        assertEquals(grpcId, onlyProfile.id)
    }

    @Test
    fun `should reuse existing profile by full identity when base key is duplicated in existing data`() =
        runBlocking {
            val g = group()
            val withVisionFlow =
                "vless://aaaaaaaa-1111-1111-1111-aaaaaaaaaaaa@dup-full.example.com:443" +
                    "?type=tcp&security=reality&pbk=pub&sid=01&flow=xtls-rprx-vision#Vision"
            val withNoFlow =
                "vless://aaaaaaaa-1111-1111-1111-aaaaaaaaaaaa@dup-full.example.com:443" +
                    "?type=tcp&security=reality&pbk=pub&sid=01#NoFlow"
            val existingVisionProfile = ru.ozero.singboxroom.entity.ProxyProfile(
                groupId = g.id,
                name = "Vision",
                beanBlob = ru.ozero.singboxfmt.KryoSerializer.serialize(
                    RawShareLinksParser.parse(withVisionFlow).single(),
                ),
                protocolType = RawUpdater.PROTOCOL_VLESS,
            )
            val existingNoFlowProfile = ru.ozero.singboxroom.entity.ProxyProfile(
                groupId = g.id,
                name = "NoFlow",
                beanBlob = ru.ozero.singboxfmt.KryoSerializer.serialize(
                    RawShareLinksParser.parse(withNoFlow).single(),
                ),
                protocolType = RawUpdater.PROTOCOL_VLESS,
            )
            val noFlowId = profileDao.insert(existingNoFlowProfile)
            profileDao.insert(existingVisionProfile)

            server.enqueue(MockResponse().setBody(withNoFlow))

            rawUpdater.refresh(g)

            assertEquals(1, profileDao.profiles.size)
            val keptProfile = profileDao.profiles.single()
            assertEquals("NoFlow", keptProfile.name)
            assertEquals(noFlowId, keptProfile.id)
        }

    @Test
    fun `should preserve duplicate Trojan ids by password when provider reorders rows`() = runBlocking {
        server.enqueue(MockResponse().setBody("$trojan1\n$trojan2"))
        val g = group()

        rawUpdater.refresh(g)
        val firstByName = profileDao.profiles.associateBy { it.name }
        val firstId = firstByName.getValue("Trojan1").id
        val secondId = firstByName.getValue("Trojan2").id

        server.enqueue(MockResponse().setBody("$trojan2\n$trojan1"))
        rawUpdater.refresh(g)
        val secondByName = profileDao.profiles.associateBy { it.name }

        assertEquals(firstId, secondByName.getValue("Trojan1").id)
        assertEquals(secondId, secondByName.getValue("Trojan2").id)
    }

    @Test
    fun `should preserve duplicate Shadowsocks ids by method and password when provider reorders rows`() = runBlocking {
        server.enqueue(MockResponse().setBody("$shadowsocks1\n$shadowsocks2"))
        val g = group()

        rawUpdater.refresh(g)
        val firstByName = profileDao.profiles.associateBy { it.name }
        val firstId = firstByName.getValue("SS1").id
        val secondId = firstByName.getValue("SS2").id

        server.enqueue(MockResponse().setBody("$shadowsocks2\n$shadowsocks1"))
        rawUpdater.refresh(g)
        val secondByName = profileDao.profiles.associateBy { it.name }

        assertEquals(firstId, secondByName.getValue("SS1").id)
        assertEquals(secondId, secondByName.getValue("SS2").id)
    }

    @Test
    fun `should preserve duplicate Shadowsocks ids by plugin when provider reorders rows`() = runBlocking {
        val pluginA =
            """
            {
              "outbounds": [
                {
                  "type": "shadowsocks",
                  "tag": "Plugin A",
                  "server": "ss-plugin.example.com",
                  "server_port": 8388,
                  "method": "aes-128-gcm",
                  "password": "same-password",
                  "plugin": "obfs-local",
                  "plugin_opts": "obfs=http"
                },
                {
                  "type": "shadowsocks",
                  "tag": "Plugin B",
                  "server": "ss-plugin.example.com",
                  "server_port": 8388,
                  "method": "aes-128-gcm",
                  "password": "same-password",
                  "plugin": "v2ray-plugin",
                  "plugin_opts": "mode=websocket"
                }
              ]
            }
            """.trimIndent()
        val pluginB =
            """
            {
              "outbounds": [
                {
                  "type": "shadowsocks",
                  "tag": "Plugin B",
                  "server": "ss-plugin.example.com",
                  "server_port": 8388,
                  "method": "aes-128-gcm",
                  "password": "same-password",
                  "plugin": "v2ray-plugin",
                  "plugin_opts": "mode=websocket"
                },
                {
                  "type": "shadowsocks",
                  "tag": "Plugin A",
                  "server": "ss-plugin.example.com",
                  "server_port": 8388,
                  "method": "aes-128-gcm",
                  "password": "same-password",
                  "plugin": "obfs-local",
                  "plugin_opts": "obfs=http"
                }
              ]
            }
            """.trimIndent()
        server.enqueue(MockResponse().setBody(pluginA))
        val g = group()

        rawUpdater.refresh(g)
        val firstByName = profileDao.profiles.associateBy { it.name }
        val pluginAId = firstByName.getValue("Plugin A").id
        val pluginBId = firstByName.getValue("Plugin B").id

        server.enqueue(MockResponse().setBody(pluginB))
        rawUpdater.refresh(g)
        val secondByName = profileDao.profiles.associateBy { it.name }

        assertEquals(pluginAId, secondByName.getValue("Plugin A").id)
        assertEquals(pluginBId, secondByName.getValue("Plugin B").id)
    }

    @Test
    fun `should not match corrupted existing blob to incoming valid profile`() = runBlocking {
        server.enqueue(MockResponse().setBody(vless1))
        val g = group()
        profileDao.profiles.add(
            ru.ozero.singboxroom.entity.ProxyProfile(
                id = 501L,
                groupId = g.id,
                name = "Corrupted",
                beanBlob = byteArrayOf(9, 8, 7),
                protocolType = RawUpdater.PROTOCOL_VLESS,
            ),
        )

        rawUpdater.refresh(g)

        assertEquals(1, profileDao.profiles.size)
        assertTrue(profileDao.profiles.none { it.id == 501L })
        assertEquals("S1", profileDao.profiles.single().name)
    }

    @Test
    fun `should preserve single existing stable id without forcing full identity`() = runBlocking {
        server.enqueue(MockResponse().setBody(vless1))
        val g = group()

        rawUpdater.refresh(g)
        val first = profileDao.profiles.single()

        val renamed =
            "vless://aaaaaaaa-1111-1111-1111-aaaaaaaaaaaa@s1.example.com:443?type=tcp&security=none#RenamedAgain"
        server.enqueue(MockResponse().setBody(renamed))
        rawUpdater.refresh(g)

        val refreshed = profileDao.profiles.single()
        assertEquals(first.id, refreshed.id)
        assertEquals("RenamedAgain", refreshed.name)
    }

    @Test
    fun `should delete stale rows when successful response has null body`() = runBlocking {
        val mockedResponse = mockk<Response>(relaxed = true).apply {
            every { body } returns null
            every { isSuccessful } returns true
            every { header("Subscription-Userinfo") } returns null
            every { code } returns 200
        }
        val mockedCall = mockk<Call>(relaxed = true)
        every { mockedCall.execute() } returns mockedResponse
        val mockedClient = mockk<OkHttpClient>()
        every { mockedClient.newCall(any()) } returns mockedCall
        rawUpdater = RawUpdater(mockedClient, groupDao, profileDao)
        val g = group()
        profileDao.profiles.add(
            ru.ozero.singboxroom.entity.ProxyProfile(
                id = 701L,
                groupId = g.id,
                name = "Stale",
                beanBlob = byteArrayOf(1, 2, 3),
                protocolType = RawUpdater.PROTOCOL_VLESS,
            ),
        )

        val result = rawUpdater.refresh(g)

        assertTrue(result.isSuccess)
        assertEquals(0, result.getOrNull())
        assertTrue(profileDao.profiles.isEmpty())
    }

    @Test
    fun `should leave unrelated group rows when refresh removes stale rows`() = runBlocking {
        server.enqueue(MockResponse().setBody(vless1))
        val target = group(id = 10L)
        val other = group(id = 11L)
        profileDao.profiles.add(
            ru.ozero.singboxroom.entity.ProxyProfile(
                id = 801L,
                groupId = target.id,
                name = "Target stale",
                beanBlob = byteArrayOf(8, 0, 1),
                protocolType = RawUpdater.PROTOCOL_VLESS,
            ),
        )
        profileDao.profiles.add(
            ru.ozero.singboxroom.entity.ProxyProfile(
                id = 802L,
                groupId = other.id,
                name = "Other",
                beanBlob = byteArrayOf(8, 0, 2),
                protocolType = RawUpdater.PROTOCOL_VLESS,
            ),
        )

        rawUpdater.refresh(target)

        assertFalse(profileDao.profiles.any { it.id == 801L })
        assertTrue(profileDao.profiles.any { it.id == 802L && it.groupId == other.id })
        assertTrue(profileDao.profiles.any { it.groupId == target.id && it.name == "S1" })
    }
}
