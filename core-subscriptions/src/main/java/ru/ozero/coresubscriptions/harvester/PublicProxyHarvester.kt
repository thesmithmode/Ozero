package ru.ozero.coresubscriptions.harvester

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import ru.ozero.corestorage.dao.ServerDao
import ru.ozero.corestorage.entity.ServerEntity
import ru.ozero.coresubscriptions.ServerMapper
import ru.ozero.coresubscriptions.uri.ParsedServer
import ru.ozero.coresubscriptions.uri.SubscriptionUriParser
import java.util.Base64

/**
 * E16.1: периодически пуллит public GitHub-репо живых прокси под РФ
 * (см. PLAN раздел 0.2), парсит URI через [SubscriptionUriParser], upsert
 * в [ServerDao].
 *
 * Контракт:
 * - Без сети не падает (источники с network-failure пропускаются)
 * - Не очищает БД при пустом результате (защита от network outage)
 * - Не валидирует «живой» сервер сейчас — это работа [E16.2 prober'а]
 *   через `ServerEntity.isAlive` (отдельный pass)
 *
 * Использует OkHttp напрямую (без middleware) — GitHub TLS, system trust,
 * никакого pinning'а (см. PLAN v4 раздел 0.6).
 */
class PublicProxyHarvester(
    private val httpClient: OkHttpClient,
    private val serverDao: ServerDao,
    private val parser: SubscriptionUriParser = SubscriptionUriParser(),
    private val mapper: ServerMapper = ServerMapper(),
) {

    suspend fun harvest(sources: List<PublicProxySource>): HarvestResult = withContext(Dispatchers.IO) {
        var totalEntities = 0
        var failed = 0
        val perSource = mutableListOf<PerSourceResult>()
        for (source in sources) {
            // HTTPS-only: HTTP-источник = MITM возможен, отбрасываем сразу.
            if (!source.url.startsWith("https://")) {
                Log.w(TAG, "source ${source.id} non-HTTPS URL — skip")
                failed++
                perSource += PerSourceResult(source.id, parsedCount = 0, error = "non-HTTPS URL")
                continue
            }
            val sr = runCatching { harvestOne(source) }.getOrElse {
                Log.w(TAG, "source ${source.id} failed", it)
                failed++
                PerSourceResult(source.id, parsedCount = 0, error = it.message)
            }
            perSource += sr
            totalEntities += sr.parsedCount
        }
        if (perSource.any { it.parsedCount > 0 }) {
            val entities = perSource.flatMap { it.entities }
            // Дедупликация по id (stable hash от originalUri)
            val unique = entities.distinctBy { it.id }
            serverDao.upsertAll(unique)
            Log.i(TAG, "harvest OK: ${unique.size} unique entities из ${sources.size} sources ($failed failed)")
        } else {
            Log.w(TAG, "harvest пустой → БД не трогаем (network outage защита)")
        }
        HarvestResult(totalParsed = totalEntities, failedSources = failed, perSource = perSource)
    }

    private fun harvestOne(source: PublicProxySource): PerSourceResult {
        val req = Request.Builder().url(source.url).get().build()
        httpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                return PerSourceResult(source.id, parsedCount = 0, error = "HTTP ${resp.code}")
            }
            val respBody = resp.body ?: return PerSourceResult(source.id, 0, error = "empty body")
            // contentLength() возвращает -1 для chunked → ему доверять нельзя.
            // Реальный лимит — стримовая проверка ниже: читаем ровно MAX_BODY_BYTES,
            // затем пробуем ещё 1 байт; если есть — превышение.
            val src = respBody.source()
            val bytes = src.readByteString(MAX_BODY_BYTES)
            if (src.request(1L)) {
                return PerSourceResult(source.id, 0, error = "body exceeds $MAX_BODY_BYTES bytes")
            }
            val body = bytes.utf8()
            val lines: Sequence<String> = when (source.format) {
                SourceFormat.LINES -> body.lineSequence()
                SourceFormat.BASE64_LINES -> {
                    val decoded = runCatching {
                        Base64.getDecoder().decode(body.trim()).toString(Charsets.UTF_8)
                    }.getOrElse { return PerSourceResult(source.id, 0, error = "base64 decode failed") }
                    decoded.lineSequence()
                }
                SourceFormat.JSON_ARRAY -> parseJsonArray(body).asSequence()
            }
            val entities = lines
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("#") }
                .mapNotNull { uri ->
                    val parsed = parser.parse(uri)
                    if (parsed is ParsedServer.Error) null else mapper.toEntity(parsed, uri)
                }
                .take(MAX_ENTITIES_PER_SOURCE)
                .toList()
            return PerSourceResult(source.id, parsedCount = entities.size, entities = entities)
        }
    }

    /**
     * Парсер `{ "servers": ["uri", ...] }` или верхнего массива через org.json.
     * Лимит [MAX_ENTITIES_PER_SOURCE] применяется один раз — через `.take()` в общей
     * цепочке `harvestOne`. Здесь только парсим, без cap.
     */
    private fun parseJsonArray(body: String): List<String> {
        val arr: JSONArray = runCatching {
            // Сначала пробуем как JSON-объект с полем servers
            val obj = JSONObject(body)
            obj.optJSONArray("servers") ?: return emptyList()
        }.getOrElse {
            runCatching { JSONArray(body) }.getOrElse { return emptyList() }
        }
        return List(arr.length()) { i -> arr.optString(i) }
    }

    data class HarvestResult(
        val totalParsed: Int,
        val failedSources: Int,
        val perSource: List<PerSourceResult>,
    )

    data class PerSourceResult(
        val sourceId: String,
        val parsedCount: Int,
        val error: String? = null,
        val entities: List<ServerEntity> = emptyList(),
    )

    private companion object {
        const val TAG = "PublicProxyHarvester"
        const val MAX_BODY_BYTES = 4L * 1024 * 1024 // 4 МБ — достаточно для тысяч URI
        const val MAX_ENTITIES_PER_SOURCE = 2000
    }
}
