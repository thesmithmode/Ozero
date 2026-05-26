package ru.ozero.app.ui.settings.engines.singbox

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.byteArrayPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import ru.ozero.enginesingbox.SingboxPrefs
import ru.ozero.singboxfmt.AbstractBean
import ru.ozero.singboxfmt.KryoSerializer
import ru.ozero.singboxroom.dao.ProxyProfileDao
import ru.ozero.singboxroom.entity.ProxyProfile
import java.net.InetSocketAddress
import java.net.Socket
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SingboxProbeService @Inject constructor(
    private val profileDao: ProxyProfileDao,
    @SingboxPrefs private val dataStore: DataStore<Preferences>,
) {

    suspend fun probeAndAutoSelect(
        profiles: List<ProxyProfile>,
        scope: CoroutineScope,
    ) {
        val results = profiles.map { profile ->
            scope.async(Dispatchers.IO) {
                val latency = probeLatencyMs(profile)
                if (latency >= 0) profileDao.updateLatency(profile.id, latency)
                profile to latency
            }
        }.awaitAll()
        val best = results.filter { it.second >= 0 }.minByOrNull { it.second }?.first ?: return
        dataStore.edit { prefs ->
            prefs[SELECTED_PROFILE_KEY] = best.id
            prefs[BEAN_KEY] = best.beanBlob
        }
    }

    private suspend fun probeLatencyMs(profile: ProxyProfile): Int = withContext(Dispatchers.IO) {
        val bean = runCatching { KryoSerializer.deserialize<AbstractBean>(profile.beanBlob) }
            .getOrNull() ?: return@withContext -1
        val t0 = System.currentTimeMillis()
        val ok = runCatching {
            Socket().use { s ->
                s.connect(InetSocketAddress(bean.serverAddress, bean.serverPort), PROBE_TIMEOUT_MS)
            }
        }.isSuccess
        if (ok) (System.currentTimeMillis() - t0).toInt() else -1
    }

    companion object {
        val BEAN_KEY = byteArrayPreferencesKey("singbox_vless_bean")
        val SELECTED_PROFILE_KEY = longPreferencesKey("singbox_selected_profile_id")
        private const val PROBE_TIMEOUT_MS = 3_000
    }
}
