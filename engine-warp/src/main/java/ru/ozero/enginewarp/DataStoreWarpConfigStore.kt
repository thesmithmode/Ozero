package ru.ozero.enginewarp

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class DataStoreWarpConfigStore(
    private val dataStore: DataStore<Preferences>,
) : WarpConfigStore {

    override fun current(): Flow<WarpConfig?> = dataStore.data.map { prefs ->
        val priv = prefs[KEY_PRIV] ?: return@map null
        val pub = prefs[KEY_PUB] ?: return@map null
        val peerPub = prefs[KEY_PEER_PUB] ?: return@map null
        val peerEndpoint = prefs[KEY_PEER_ENDPOINT] ?: return@map null
        val v4 = prefs[KEY_IFACE_V4] ?: return@map null
        val v6 = prefs[KEY_IFACE_V6] ?: return@map null
        val license = prefs[KEY_LICENSE] ?: return@map null
        val mtu = prefs[KEY_MTU] ?: DEFAULT_MTU
        WarpConfig(
            privateKey = priv,
            publicKey = pub,
            peerPublicKey = peerPub,
            peerEndpoint = peerEndpoint,
            interfaceAddressV4 = v4,
            interfaceAddressV6 = v6,
            accountLicense = license,
            mtu = mtu,
        )
    }

    override suspend fun save(config: WarpConfig) {
        dataStore.edit { prefs ->
            prefs[KEY_PRIV] = config.privateKey
            prefs[KEY_PUB] = config.publicKey
            prefs[KEY_PEER_PUB] = config.peerPublicKey
            prefs[KEY_PEER_ENDPOINT] = config.peerEndpoint
            prefs[KEY_IFACE_V4] = config.interfaceAddressV4
            prefs[KEY_IFACE_V6] = config.interfaceAddressV6
            prefs[KEY_LICENSE] = config.accountLicense
            prefs[KEY_MTU] = config.mtu
        }
    }

    override suspend fun clear() {
        dataStore.edit { prefs ->
            prefs.remove(KEY_PRIV)
            prefs.remove(KEY_PUB)
            prefs.remove(KEY_PEER_PUB)
            prefs.remove(KEY_PEER_ENDPOINT)
            prefs.remove(KEY_IFACE_V4)
            prefs.remove(KEY_IFACE_V6)
            prefs.remove(KEY_LICENSE)
            prefs.remove(KEY_MTU)
        }
    }

    private companion object {
        const val DEFAULT_MTU = 1280
        val KEY_PRIV = stringPreferencesKey("warp_priv")
        val KEY_PUB = stringPreferencesKey("warp_pub")
        val KEY_PEER_PUB = stringPreferencesKey("warp_peer_pub")
        val KEY_PEER_ENDPOINT = stringPreferencesKey("warp_peer_endpoint")
        val KEY_IFACE_V4 = stringPreferencesKey("warp_iface_v4")
        val KEY_IFACE_V6 = stringPreferencesKey("warp_iface_v6")
        val KEY_LICENSE = stringPreferencesKey("warp_license")
        val KEY_MTU = intPreferencesKey("warp_mtu")
    }
}
