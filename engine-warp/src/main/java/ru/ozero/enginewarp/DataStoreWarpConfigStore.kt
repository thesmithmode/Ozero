package ru.ozero.enginewarp

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ru.ozero.enginescore.PersistentLoggers

class DataStoreWarpConfigStore(
    private val dataStore: DataStore<Preferences>,
) : WarpConfigStore {

    override fun current(): Flow<WarpConfig?> = dataStore.data.map { prefs ->
        val priv = prefs[KEY_PRIV] ?: return@map null
        val peerPub = prefs[KEY_PEER_PUB] ?: return@map null
        val peerEndpoint = prefs[KEY_PEER_ENDPOINT] ?: return@map null
        val v4 = prefs[KEY_IFACE_V4] ?: return@map null
        val v6 = prefs[KEY_IFACE_V6] ?: return@map null
        val pub = prefs[KEY_PUB].orEmpty()
        val license = prefs[KEY_LICENSE].orEmpty()
        val mtu = prefs[KEY_MTU] ?: WarpConfig.DEFAULT_MTU
        val dnsServers = prefs[KEY_DNS]
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.takeIf { it.isNotEmpty() }
            ?: WarpConfig.DEFAULT_DNS
        val keepalive = prefs[KEY_KEEPALIVE] ?: WarpConfig.DEFAULT_KEEPALIVE
        val allowedIps = prefs[KEY_ALLOWED_IPS]
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.takeIf { it.isNotEmpty() }
            ?: WarpConfig.DEFAULT_ALLOWED_IPS
        val awgParams = AwgParams(
            junkPacketCount = prefs[KEY_AWG_JC] ?: AwgParams.DEFAULT_JC,
            junkPacketMinSize = prefs[KEY_AWG_JMIN] ?: AwgParams.DEFAULT_JMIN,
            junkPacketMaxSize = prefs[KEY_AWG_JMAX] ?: AwgParams.DEFAULT_JMAX,
            initPacketJunkSize = prefs[KEY_AWG_S1] ?: AwgParams.DEFAULT_S1,
            responsePacketJunkSize = prefs[KEY_AWG_S2] ?: AwgParams.DEFAULT_S2,
            initPacketMagicHeader = parseLongPref(prefs[KEY_AWG_H1], "H1", AwgParams.DEFAULT_H1),
            responsePacketMagicHeader = parseLongPref(prefs[KEY_AWG_H2], "H2", AwgParams.DEFAULT_H2),
            cookieReplyMagicHeader = parseLongPref(prefs[KEY_AWG_H3], "H3", AwgParams.DEFAULT_H3),
            transportMagicHeader = parseLongPref(prefs[KEY_AWG_H4], "H4", AwgParams.DEFAULT_H4),
        )
        WarpConfig(
            privateKey = priv,
            publicKey = pub,
            peerPublicKey = peerPub,
            peerEndpoint = peerEndpoint,
            interfaceAddressV4 = v4,
            interfaceAddressV6 = v6,
            accountLicense = license,
            mtu = mtu,
            dnsServers = dnsServers,
            keepaliveSeconds = keepalive,
            allowedIps = allowedIps,
            awgParams = awgParams,
            doHProvider = DoHProvider.SYSTEM,
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
            prefs[KEY_DNS] = config.dnsServers.joinToString(",")
            prefs[KEY_KEEPALIVE] = config.keepaliveSeconds
            prefs[KEY_ALLOWED_IPS] = config.allowedIps.joinToString(",")
            prefs[KEY_AWG_JC] = config.awgParams.junkPacketCount
            prefs[KEY_AWG_JMIN] = config.awgParams.junkPacketMinSize
            prefs[KEY_AWG_JMAX] = config.awgParams.junkPacketMaxSize
            prefs[KEY_AWG_S1] = config.awgParams.initPacketJunkSize
            prefs[KEY_AWG_S2] = config.awgParams.responsePacketJunkSize
            prefs[KEY_AWG_H1] = config.awgParams.initPacketMagicHeader.toString()
            prefs[KEY_AWG_H2] = config.awgParams.responsePacketMagicHeader.toString()
            prefs[KEY_AWG_H3] = config.awgParams.cookieReplyMagicHeader.toString()
            prefs[KEY_AWG_H4] = config.awgParams.transportMagicHeader.toString()
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
            prefs.remove(KEY_DNS)
            prefs.remove(KEY_KEEPALIVE)
            prefs.remove(KEY_ALLOWED_IPS)
            prefs.remove(KEY_AWG_JC)
            prefs.remove(KEY_AWG_JMIN)
            prefs.remove(KEY_AWG_JMAX)
            prefs.remove(KEY_AWG_S1)
            prefs.remove(KEY_AWG_S2)
            prefs.remove(KEY_AWG_H1)
            prefs.remove(KEY_AWG_H2)
            prefs.remove(KEY_AWG_H3)
            prefs.remove(KEY_AWG_H4)
        }
    }

    private fun parseLongPref(raw: String?, key: String, default: Long): Long {
        if (raw == null) return default
        return raw.toLongOrNull() ?: run {
            PersistentLoggers.warn(TAG, "AWG $key parse failed ($raw), using default $default")
            default
        }
    }

    private companion object {
        const val TAG = "DataStoreWarpConfigStore"
        val KEY_PRIV = stringPreferencesKey("warp_priv")
        val KEY_PUB = stringPreferencesKey("warp_pub")
        val KEY_PEER_PUB = stringPreferencesKey("warp_peer_pub")
        val KEY_PEER_ENDPOINT = stringPreferencesKey("warp_peer_endpoint")
        val KEY_IFACE_V4 = stringPreferencesKey("warp_iface_v4")
        val KEY_IFACE_V6 = stringPreferencesKey("warp_iface_v6")
        val KEY_LICENSE = stringPreferencesKey("warp_license")
        val KEY_MTU = intPreferencesKey("warp_mtu")
        val KEY_DNS = stringPreferencesKey("warp_dns")
        val KEY_KEEPALIVE = intPreferencesKey("warp_keepalive")
        val KEY_ALLOWED_IPS = stringPreferencesKey("warp_allowed_ips")
        val KEY_AWG_JC = intPreferencesKey("awg_jc")
        val KEY_AWG_JMIN = intPreferencesKey("awg_jmin")
        val KEY_AWG_JMAX = intPreferencesKey("awg_jmax")
        val KEY_AWG_S1 = intPreferencesKey("awg_s1")
        val KEY_AWG_S2 = intPreferencesKey("awg_s2")
        val KEY_AWG_H1 = stringPreferencesKey("awg_h1")
        val KEY_AWG_H2 = stringPreferencesKey("awg_h2")
        val KEY_AWG_H3 = stringPreferencesKey("awg_h3")
        val KEY_AWG_H4 = stringPreferencesKey("awg_h4")
    }
}
