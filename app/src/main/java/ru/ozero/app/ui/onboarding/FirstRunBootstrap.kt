package ru.ozero.app.ui.onboarding

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import ru.ozero.app.subscription.ServerImportService
import ru.ozero.commoncrypto.Ed25519PemLoader
import ru.ozero.commoncrypto.Ed25519Verifier
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
 *
 * Безопасность: snapshot подписан Ed25519, подпись `bootstrap-servers.json.sig`,
 * публичный ключ `update-pubkey.pem`. Fail-closed: если проверка не прошла —
 * импорт пропускается полностью (без crash, без частичного импорта).
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
                val raw = context.assets.open(ASSET_NAME).use { it.readBytes() }
                val sig = context.assets.open(SIG_NAME).use { it.readBytes() }
                val pem = context.assets.open(PUBKEY_NAME).bufferedReader().use { it.readText() }

                val pubKey = runCatching { Ed25519PemLoader.parsePublicKey(pem) }.getOrElse {
                    Log.e(TAG, "FAIL-CLOSED: cannot parse $PUBKEY_NAME: ${it.message}")
                    return@runCatching
                }

                val ok = Ed25519Verifier.verify(raw, sig, pubKey)
                if (!ok) {
                    Log.e(
                        TAG,
                        "FAIL-CLOSED: Ed25519 signature mismatch for $ASSET_NAME " +
                            "(raw=${raw.size}B, sig=${sig.size}B). Skipping bootstrap import.",
                    )
                    return@runCatching
                }
                Log.i(TAG, "Ed25519 signature verified for $ASSET_NAME (${raw.size}B)")

                val json = JSONObject(String(raw, Charsets.UTF_8))
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
        const val SIG_NAME = "bootstrap-servers.json.sig"
        const val PUBKEY_NAME = "update-pubkey.pem"
    }
}
