package ru.ozero.singboxsubscription

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ru.ozero.singboxroom.entity.SubscriptionGroup
import kotlin.test.assertEquals

class RawUpdaterStableIdTest {
    private val server = MockWebServer()
    private lateinit var groupDao: FakeSubscriptionGroupDao
    private lateinit var profileDao: FakeProxyProfileDao
    private lateinit var rawUpdater: RawUpdater

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

    @Test
    fun `should avoid base identity reuse when existing duplicate rows collapse to one incoming row`() = runBlocking {
        val ws =
            "vless://aaaaaaaa-1111-1111-1111-aaaaaaaaaaaa@dup.example.com:443" +
                "?type=ws&security=tls&sni=ws.example.com&host=front.example.com&path=/ws#WS"
        val grpc =
            "vless://aaaaaaaa-1111-1111-1111-aaaaaaaaaaaa@dup.example.com:443" +
                "?type=grpc&security=tls&sni=grpc.example.com&serviceName=grpc-svc#GRPC"
        server.enqueue(MockResponse().setBody("$ws\n$grpc"))
        val group = group()

        rawUpdater.refresh(group)
        val grpcId = profileDao.profiles.single { it.name == "GRPC" }.id

        server.enqueue(MockResponse().setBody(grpc))
        rawUpdater.refresh(group)

        val refreshed = profileDao.profiles.single()
        assertEquals("GRPC", refreshed.name)
        assertEquals(grpcId, refreshed.id)
    }

    private fun group(id: Long = 1L): SubscriptionGroup {
        val group = SubscriptionGroup(id = id, name = "Test", subscriptionUrl = server.url("/sub").toString())
        groupDao.groups.add(group)
        return group
    }
}
