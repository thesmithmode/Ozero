package ru.ozero.app.subscription

import android.util.Log
import ru.ozero.corestorage.dao.ServerDao
import ru.ozero.corestorage.entity.ServerEntity
import ru.ozero.coresubscriptions.ServerMapper
import ru.ozero.coresubscriptions.SubscriptionFilter
import ru.ozero.coresubscriptions.uri.ParsedServer
import ru.ozero.coresubscriptions.uri.SubscriptionUriParser
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ServerImportService @Inject constructor(
    private val serverDao: ServerDao,
    private val parser: SubscriptionUriParser = SubscriptionUriParser(),
    private val mapper: ServerMapper = ServerMapper(),
    private val filter: SubscriptionFilter = SubscriptionFilter(),
) {

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
        if (!filter.isLiveIn2026(parsed)) {
            Log.w(TAG, "import REJECT: protocol/config не пройдёт ТСПУ в 2026")
            return ImportResult.Error("протокол/конфиг не пройдёт фильтрацию (нужен Reality/Hy2/Trojan/Naive/AWG2)")
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
