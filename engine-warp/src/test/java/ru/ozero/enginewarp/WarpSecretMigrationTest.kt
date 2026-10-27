package ru.ozero.enginewarp

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.ozero.commoncrypto.PreferenceAtRest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WarpSecretMigrationTest {
    @TempDir
    lateinit var tmp: File

    @BeforeEach
    fun installCipher() {
        installDefaultAtRestCipher()
    }

    @Test
    fun `plaintext warp priv is migrated and the old key is removed`() = runTest {
        val dataStore = newStore("warp-priv.preferences_pb")
        dataStore.edit { prefs ->
            prefs[KEY_PRIV] = PRIV
            prefs[stringPreferencesKey("warp_peer_pub")] = "peer"
            prefs[stringPreferencesKey("warp_peer_endpoint")] = "host:2408"
            prefs[stringPreferencesKey("warp_iface_v4")] = "172.16.0.2/32"
            prefs[stringPreferencesKey("warp_iface_v6")] = "2606:4700::1/128"
        }

        val read = DataStoreWarpConfigStore(dataStore).current().first()

        assertEquals(PRIV, read?.privateKey)
        val prefs = dataStore.data.first()
        assertNull(prefs[KEY_PRIV])
        val sealed = prefs[KEY_PRIV_ENC]
        assertTrue(sealed != null && sealed.startsWith("enc1:"))
        assertFalse(sealed.contains(PRIV))
        assertEquals(PRIV, PreferenceAtRest.open(sealed))
    }

    @Test
    fun `already encrypted warp priv is read back unchanged on a second read`() = runTest {
        val sealed = PreferenceAtRest.seal(PRIV)
        val dataStore = newStore("warp-priv-enc.preferences_pb")
        dataStore.edit { prefs ->
            prefs[KEY_PRIV_ENC] = sealed
            prefs[stringPreferencesKey("warp_peer_pub")] = "peer"
            prefs[stringPreferencesKey("warp_peer_endpoint")] = "host:2408"
            prefs[stringPreferencesKey("warp_iface_v4")] = "172.16.0.2/32"
            prefs[stringPreferencesKey("warp_iface_v6")] = "2606:4700::1/128"
        }
        val store = DataStoreWarpConfigStore(dataStore)

        assertEquals(PRIV, store.current().first()?.privateKey)
        assertEquals(PRIV, store.current().first()?.privateKey)
        assertEquals(sealed, dataStore.data.first()[KEY_PRIV_ENC])
        assertNull(dataStore.data.first()[KEY_PRIV])
    }

    @Test
    fun `plaintext slot private key is migrated and a second read does not reseal`() = runTest {
        val rawIni = "[Interface]\nPrivateKey = $INI_SECRET\nJc = 0\n"
        val slot = JSONObject()
            .put("id", "slot-1")
            .put("name", "One")
            .put("isActive", true)
            .put("rawIni", rawIni)
            .put(
                "config",
                JSONObject()
                    .put("priv", PRIV)
                    .put("pub", "pub")
                    .put("peerPub", "peer")
                    .put("peerEndpoint", "host:2408")
                    .put("ifaceV4", "172.16.0.2/32")
                    .put("ifaceV6", "2606:4700::1/128"),
            )
        val dataStore = newStore("warp-slots.preferences_pb")
        dataStore.edit { prefs ->
            prefs[KEY_SLOTS] = JSONArray().put(slot).toString()
        }
        val legacy = PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = { File(tmp, "warp-legacy.preferences_pb") },
        )
        val store = DataStoreWarpConfigSlotStore(dataStore, DataStoreWarpConfigStore(legacy))

        val first = store.slots().first().single()
        val storedOnce = dataStore.data.first()[KEY_SLOTS]!!
        val second = store.slots().first().single()
        val storedTwice = dataStore.data.first()[KEY_SLOTS]!!

        assertEquals(PRIV, first.config.privateKey)
        assertEquals(PRIV, second.config.privateKey)
        assertEquals(rawIni, first.rawIniOverride)
        assertEquals(storedOnce, storedTwice)
        assertFalse(storedTwice.contains(PRIV))
        assertFalse(storedTwice.contains(INI_SECRET))
        val storedPriv = JSONArray(storedTwice).getJSONObject(0).getJSONObject("config").getString("priv")
        assertTrue(storedPriv.startsWith("enc1:"))
        assertEquals(PRIV, PreferenceAtRest.open(storedPriv))
        val storedIni = JSONArray(storedTwice).getJSONObject(0).getString("rawIni")
        assertTrue(storedIni.startsWith("enc1:"))
        assertEquals(rawIni, PreferenceAtRest.open(storedIni))
    }

    @Test
    fun `already encrypted slot private key is opened without rewriting`() = runTest {
        val sealed = PreferenceAtRest.seal(PRIV)
        val slot = JSONObject()
            .put("id", "slot-enc")
            .put("name", "Enc")
            .put("isActive", true)
            .put(
                "config",
                JSONObject()
                    .put("priv", sealed)
                    .put("pub", "pub")
                    .put("peerPub", "peer")
                    .put("peerEndpoint", "host:2408")
                    .put("ifaceV4", "172.16.0.2/32")
                    .put("ifaceV6", "2606:4700::1/128"),
            )
        val original = JSONArray().put(slot).toString()
        val dataStore = newStore("warp-slots-enc.preferences_pb")
        dataStore.edit { prefs -> prefs[KEY_SLOTS] = original }
        val legacy = PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = { File(tmp, "warp-legacy-enc.preferences_pb") },
        )
        val store = DataStoreWarpConfigSlotStore(dataStore, DataStoreWarpConfigStore(legacy))

        assertEquals(PRIV, store.slots().first().single().config.privateKey)
        assertEquals(PRIV, store.slots().first().single().config.privateKey)
        assertEquals(original, dataStore.data.first()[KEY_SLOTS])
    }

    private fun TestScope.newStore(name: String) = PreferenceDataStoreFactory.create(
        scope = backgroundScope,
        produceFile = { File(tmp, name) },
    )

    private companion object {
        val KEY_PRIV = stringPreferencesKey("warp_priv")
        val KEY_PRIV_ENC = stringPreferencesKey("warp_priv_enc")
        val KEY_SLOTS = stringPreferencesKey("warp_slots_json")
        const val PRIV = "warp-private-key"
        const val INI_SECRET = "ini-private-key"
    }
}
