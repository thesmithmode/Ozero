package ru.ozero.app.ui.settings.engines.singbox

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.byteArrayPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import ru.ozero.enginesingbox.SingboxEngine
import ru.ozero.enginesingbox.SingboxPrefs
import ru.ozero.singboxconfig.ConfigBuilder
import ru.ozero.singboxfmt.AbstractBean
import ru.ozero.singboxfmt.KryoSerializer
import ru.ozero.singboxroom.dao.ProxyProfileDao
import ru.ozero.singboxroom.entity.ProxyProfile
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SingboxProbeService @Inject constructor(
    private val profileDao: ProxyProfileDao,
    @SingboxPrefs private val dataStore: DataStore<Preferences>,
) {

    suspend fun probeAndAutoSelect(profiles: List<ProxyProfile>) {
        val candidates = profiles.mapNotNull { profile ->
            val bean = runCatching { KryoSerializer.deserialize<AbstractBean>(profile.beanBlob) }.getOrNull()
            if (bean == null || !ConfigBuilder.isSupportedBean(bean)) {
                profileDao.updateLatency(profile.id, LATENCY_FAILED)
                null
            } else {
                profile
            }
        }
        val best = candidates.minWithOrNull(
            compareBy<ProxyProfile> { if (it.latencyMs >= 0) it.latencyMs else Int.MAX_VALUE }
                .thenBy { it.userOrder }
                .thenBy { it.id },
        ) ?: return
        dataStore.edit { prefs ->
            if (prefs[SELECTED_PROFILE_KEY] == SingboxEngine.SELECTED_AUTO) return@edit
            prefs[SELECTED_PROFILE_KEY] = best.id
            prefs[BEAN_KEY] = best.beanBlob
        }
    }

    companion object {
        val BEAN_KEY = byteArrayPreferencesKey("singbox_vless_bean")
        val SELECTED_PROFILE_KEY = longPreferencesKey("singbox_selected_profile_id")
        const val LATENCY_UNTESTED = -1
        const val LATENCY_FAILED = -2
    }
}
