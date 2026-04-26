package ru.ozero.app.ui.onboarding

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import ru.ozero.app.subscription.ServerImportService
import javax.inject.Inject
import javax.inject.Singleton

/**
 * RT.9.2 / E16.4: первичный bootstrap при первом запуске приложения.
 *
 * Server-less архитектура (PLAN v4): backend `sub.ozero.app` отсутствует. Источник
 * первичного списка серверов — `app/src/main/assets/bootstrap-servers.json`,
 * snapshot'нутый из публичных GitHub-репо живых прокси под РФ на момент tag.
 * После первого pull `PublicProxyHarvester` (E16.1) — bootstrap-серверы
 * переотмечаются как dead если устарели.
 */
interface FirstRunBootstrap {
    suspend fun runIfFirstStart()
}

@Singleton
class AssetsFirstRunBootstrap @Inject constructor(
    @ApplicationContext private val context: Context,
    private val importer: ServerImportService,
) : FirstRunBootstrap {

    override suspend fun runIfFirstStart() {
        withContext(Dispatchers.IO) {
            runCatching {
                val raw = context.assets.open(ASSET_NAME).bufferedReader().use { it.readText() }
                val json = JSONObject(raw)
                val arr = json.optJSONArray("servers") ?: return@runCatching
                var imported = 0
                for (i in 0 until arr.length()) {
                    val uri = arr.optString(i).orEmpty()
                    if (uri.isBlank() || uri.contains("placeholder")) continue
                    val result = importer.import(uri)
                    if (result is ServerImportService.ImportResult.Ok) imported++
                }
                Log.i(TAG, "bootstrap from $ASSET_NAME → imported=$imported / total=${arr.length()}")
            }.onFailure { Log.w(TAG, "bootstrap failed", it) }
        }
    }

    private companion object {
        const val TAG = "AssetsFirstRunBootstrap"
        const val ASSET_NAME = "bootstrap-servers.json"
    }
}
