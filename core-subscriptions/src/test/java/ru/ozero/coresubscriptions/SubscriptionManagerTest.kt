package ru.ozero.coresubscriptions

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import ru.ozero.commoncrypto.SubscriptionVerifier
import ru.ozero.corestorage.dao.ServerDao
import ru.ozero.corestorage.entity.ServerEntity
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SubscriptionManagerTest {
    private val source: SubscriptionSource = mockk()
    private val dao: ServerDao = mockk(relaxed = true)

    private fun manager(verifyResult: Boolean = true): SubscriptionManager =
        SubscriptionManager(
            source = source,
            serverDao = dao,
            verify = { _, _, _ -> verifyResult },
            publicKey = ByteArray(32),
        )

    @Test
    fun syncFetchFailureReturnsError() = runTest {
        coEvery { source.fetch(any()) } returns SubscriptionFetchResult.Failure("timeout")
        val result = manager().sync("https://sub.example/servers.json")
        assertIs<SubscriptionSyncResult.Error>(result)
    }

    @Test
    fun syncMissingSignatureReturnsError() = runTest {
        coEvery { source.fetch(any()) } returns
            SubscriptionFetchResult.Success(body = "x".toByteArray(), signature = null)
        val result = manager().sync("https://sub.example/servers.json")
        assertIs<SubscriptionSyncResult.Error>(result)
    }

    @Test
    fun syncInvalidSignatureReturnsError() = runTest {
        coEvery { source.fetch(any()) } returns
            SubscriptionFetchResult.Success(body = "x".toByteArray(), signature = ByteArray(64))
        val result = manager(verifyResult = false).sync("https://sub.example/servers.json")
        assertIs<SubscriptionSyncResult.Error>(result)
    }

    @Test
    fun syncParsesServersAndPersists() = runTest {
        val body =
            """
            vless://UUID@host.io:443?security=reality
            hysteria2://pass@h2.io:443
            ss://aes:p@ss.io:8388
            """.trimIndent().toByteArray()
        coEvery { source.fetch(any()) } returns
            SubscriptionFetchResult.Success(body = body, signature = ByteArray(64))

        val result = manager().sync("https://sub.example/servers.json")
        assertIs<SubscriptionSyncResult.Ok>(result)
        // 3 всего, ss и обычный vless без Reality — фильтр пропускает VLESS+reality, Hy2, (не SS)
        // Сейчас: vless(reality)=live, hy2=live, ss=dead → 2 live
        assertEquals(2, result.liveCount)
        coVerify { dao.upsertAll(match { it.size == 2 }) }
    }

    @Test
    fun syncIgnoresMalformedLines() = runTest {
        val body =
            """

            # comment
            not-a-uri
            vless://UUID@host.io:443?security=reality
            """.trimIndent().toByteArray()
        coEvery { source.fetch(any()) } returns
            SubscriptionFetchResult.Success(body = body, signature = ByteArray(64))

        val result = manager().sync("https://sub.example/servers.json")
        assertIs<SubscriptionSyncResult.Ok>(result)
        assertEquals(1, result.liveCount)
        coVerify { dao.upsertAll(match<List<ServerEntity>> { it.size == 1 && it[0].protocol == "vless" }) }
    }

    @Test
    fun syncEmptyLiveListReturnsErrorAndDoesNotPersist() = runTest {
        val body = "# only comments\n\n# nothing live".toByteArray()
        coEvery { source.fetch(any()) } returns
            SubscriptionFetchResult.Success(body = body, signature = ByteArray(64))
        val result = manager().sync("https://sub.example/servers.json")
        assertIs<SubscriptionSyncResult.Error>(result)
        io.mockk.coVerify(exactly = 0) { dao.upsertAll(any()) }
    }

    @Test
    fun verifierIsWiredToBouncyCastle() {
        // smoke-тест: production верификатор не бросает на mock данных
        val result = SubscriptionVerifier.verify(
            message = ByteArray(0),
            signature = ByteArray(64),
            publicKey = ByteArray(32),
        )
        assertEquals(false, result)
    }
}
