package ru.ozero.app.subscription

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONObject
import ru.ozero.coresubscriptions.harvester.PublicProxySource
import ru.ozero.coresubscriptions.harvester.Region
import ru.ozero.coresubscriptions.harvester.SourceFormat
import javax.inject.Inject
import javax.inject.Singleton

/**
 * E16.1: читает `app/src/main/assets/proxy-sources.json` и возвращает список
 * [PublicProxySource]. Изменение списка = перевыпуск APK; runtime-управление
 * (приоритет в выборке) — отдельная логика в `StrategyEngine`.
 */
@Singleton
class ProxySourceRegistry @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    fun load(): List<PublicProxySource> {
        return runCatching {
            val raw = context.assets.open(ASSET).bufferedReader().use { it.readText() }
            val arr = JSONObject(raw).optJSONArray("sources") ?: return emptyList()
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val format = runCatching { SourceFormat.valueOf(obj.optString("format")) }
                        .getOrNull() ?: continue
                    val region = runCatching { Region.valueOf(obj.optString("region", "GLOBAL")) }
                        .getOrDefault(Region.GLOBAL)
                    add(
                        PublicProxySource(
                            id = obj.optString("id"),
                            url = obj.optString("url"),
                            format = format,
                            region = region,
                            priority = obj.optInt("priority", 5),
                        ),
                    )
                }
            }.sortedBy { it.priority }
        }.onFailure { Log.w(TAG, "load $ASSET failed", it) }
            .getOrDefault(emptyList())
    }

    private companion object {
        const val ASSET = "proxy-sources.json"
        const val TAG = "ProxySourceRegistry"
    }
}
