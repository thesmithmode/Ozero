package ru.ozero.coresubscriptions

import android.util.Log
import ru.ozero.commoncrypto.SubscriptionVerifier
import ru.ozero.corestorage.dao.ServerDao
import ru.ozero.corestorage.entity.ServerEntity
import ru.ozero.coresubscriptions.uri.SubscriptionUriParser

sealed class SubscriptionSyncResult {
    data class Ok(val liveCount: Int, val totalCount: Int) : SubscriptionSyncResult()
    data class Error(val reason: String) : SubscriptionSyncResult()
}

typealias VerifyFn = (ByteArray, ByteArray, ByteArray) -> Boolean

class SubscriptionManager(
    private val source: SubscriptionSource,
    private val serverDao: ServerDao,
    private val publicKey: ByteArray,
    private val parser: SubscriptionUriParser = SubscriptionUriParser(),
    private val filter: SubscriptionFilter = SubscriptionFilter(),
    private val mapper: ServerMapper = ServerMapper(),
    private val verify: VerifyFn = SubscriptionVerifier::verify,
) {

    suspend fun sync(url: String): SubscriptionSyncResult {
        Log.i(TAG, "sync $url")
        val fetch = source.fetch(url)
        if (fetch is SubscriptionFetchResult.Failure) {
            return SubscriptionSyncResult.Error("fetch: ${fetch.reason}")
        }
        val success = fetch as SubscriptionFetchResult.Success

        val signature = success.signature
            ?: return SubscriptionSyncResult.Error("отсутствует подпись")

        if (!verify(success.body, signature, publicKey)) {
            Log.e(TAG, "подпись НЕ валидна")
            return SubscriptionSyncResult.Error("подпись не валидна")
        }

        val lines =
            success.body.toString(Charsets.UTF_8)
                .lineSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("#") }
                .toList()

        val entities = mutableListOf<ServerEntity>()
        for (line in lines) {
            val parsed = parser.parse(line)
            if (!filter.isLiveIn2026(parsed)) continue
            val entity = mapper.toEntity(parsed, line) ?: continue
            entities += entity
        }

        if (entities.isEmpty()) {
                        Log.w(TAG, "нет live серверов — не трогаем БД")
            return SubscriptionSyncResult.Error("нет живых серверов в подписке")
        }

        serverDao.upsertAll(entities)
        Log.i(TAG, "sync OK: ${entities.size} live из ${lines.size} строк")
        return SubscriptionSyncResult.Ok(liveCount = entities.size, totalCount = lines.size)
    }

    private companion object {
        const val TAG = "SubscriptionManager"
    }
}
