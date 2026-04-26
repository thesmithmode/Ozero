package ru.ozero.app.subscription

import android.util.Log
import ru.ozero.corestorage.dao.ServerDao
import ru.ozero.corestorage.entity.ServerEntity
import ru.ozero.coresubscriptions.ServerMapper
import ru.ozero.coresubscriptions.uri.ParsedServer
import ru.ozero.coresubscriptions.uri.SubscriptionUriParser
import javax.inject.Inject
import javax.inject.Singleton

/**
 * RT.8.1/8.2: импорт одного URI (deeplink или share-target) в локальную БД серверов.
 *
 * Pipeline такой же как у [ru.ozero.coresubscriptions.SubscriptionManager.sync],
 * но без fetch/Ed25519 — пользователь явно вставляет URI, доверие = действие.
 */
@Singleton
class ServerImportService(
    private val serverDao: ServerDao,
    private val parser: SubscriptionUriParser,
    private val mapper: ServerMapper,
) {

    @Inject constructor(serverDao: ServerDao) : this(
        serverDao = serverDao,
        parser = SubscriptionUriParser(),
        mapper = ServerMapper(),
    )

    suspend fun import(rawUri: String): ImportResult {
        val trimmed = rawUri.trim()
        if (trimmed.isEmpty()) {
            return ImportResult.Error("пустой URI")
        }
        val parsed = parser.parse(trimmed)
        if (parsed is ParsedServer.Error) {
            Log.w(TAG, "parse error: ${parsed.reason}")
            return ImportResult.Error(parsed.reason)
        }
        val entity: ServerEntity = mapper.toEntity(parsed, trimmed)
            ?: return ImportResult.Error("маппинг не дал ServerEntity")

        serverDao.upsertAll(listOf(entity))
        Log.i(TAG, "import OK protocol=${entity.protocol} country=${entity.country}")
        return ImportResult.Ok(entity)
    }

    sealed class ImportResult {
        data class Ok(val entity: ServerEntity) : ImportResult()
        data class Error(val reason: String) : ImportResult()
    }

    private companion object {
        const val TAG = "ServerImportService"
    }
}
