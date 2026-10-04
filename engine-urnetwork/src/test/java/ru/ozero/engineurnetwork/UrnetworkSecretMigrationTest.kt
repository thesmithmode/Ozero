package ru.ozero.engineurnetwork

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.ozero.commoncrypto.PreferenceAtRest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UrnetworkSecretMigrationTest {
    @TempDir
    lateinit var tmp: File

    @BeforeEach
    fun installCipher() {
        installDefaultAtRestCipher()
    }

    @Test
    fun `plaintext jwts are migrated and the old keys are removed`() = runTest {
        val dataStore = newStore("urn-plain.preferences_pb")
        dataStore.edit { prefs ->
            prefs[KEY_BY_JWT] = BY_JWT
            prefs[KEY_BY_CLIENT_JWT] = BY_CLIENT
        }

        val snap = DataStoreUrnetworkConfigStore(dataStore).config().first()

        assertEquals(BY_JWT, snap.byJwt)
        assertEquals(BY_CLIENT, snap.byClientJwt)
        val prefs = dataStore.data.first()
        assertNull(prefs[KEY_BY_JWT])
        assertNull(prefs[KEY_BY_CLIENT_JWT])
        val sealedBy = prefs[KEY_BY_JWT_ENC]
        val sealedClient = prefs[KEY_BY_CLIENT_JWT_ENC]
        assertTrue(sealedBy != null && sealedBy.startsWith("enc1:") && !sealedBy.contains(BY_JWT))
        assertTrue(sealedClient != null && sealedClient.startsWith("enc1:") && !sealedClient.contains(BY_CLIENT))
        assertEquals(BY_JWT, PreferenceAtRest.open(sealedBy))
        assertEquals(BY_CLIENT, PreferenceAtRest.open(sealedClient))
    }

    @Test
    fun `already encrypted jwts are read back as the original secrets`() = runTest {
        val sealedBy = PreferenceAtRest.seal(BY_JWT)
        val sealedClient = PreferenceAtRest.seal(BY_CLIENT)
        val dataStore = newStore("urn-enc.preferences_pb")
        dataStore.edit { prefs ->
            prefs[KEY_BY_JWT_ENC] = sealedBy
            prefs[KEY_BY_CLIENT_JWT_ENC] = sealedClient
        }

        val snap = DataStoreUrnetworkConfigStore(dataStore).config().first()

        assertEquals(BY_JWT, snap.byJwt)
        assertEquals(BY_CLIENT, snap.byClientJwt)
        val prefs = dataStore.data.first()
        assertEquals(sealedBy, prefs[KEY_BY_JWT_ENC])
        assertEquals(sealedClient, prefs[KEY_BY_CLIENT_JWT_ENC])
        assertNull(prefs[KEY_BY_JWT])
        assertNull(prefs[KEY_BY_CLIENT_JWT])
    }

    @Test
    fun `second read does not double encrypt jwts`() = runTest {
        val dataStore = newStore("urn-twice.preferences_pb")
        dataStore.edit { prefs ->
            prefs[KEY_BY_JWT] = BY_JWT
            prefs[KEY_BY_CLIENT_JWT] = BY_CLIENT
        }
        val store = DataStoreUrnetworkConfigStore(dataStore)

        assertEquals(BY_JWT, store.config().first().byJwt)
        val firstBy = dataStore.data.first()[KEY_BY_JWT_ENC]
        val firstClient = dataStore.data.first()[KEY_BY_CLIENT_JWT_ENC]
        assertEquals(BY_CLIENT, store.config().first().byClientJwt)
        val second = dataStore.data.first()

        assertEquals(firstBy, second[KEY_BY_JWT_ENC])
        assertEquals(firstClient, second[KEY_BY_CLIENT_JWT_ENC])
        assertEquals(BY_JWT, PreferenceAtRest.open(second[KEY_BY_JWT_ENC]!!))
        assertEquals(BY_CLIENT, PreferenceAtRest.open(second[KEY_BY_CLIENT_JWT_ENC]!!))
        assertFalse(second[KEY_BY_JWT_ENC]!!.contains(BY_JWT))
    }

    private fun TestScope.newStore(name: String) = PreferenceDataStoreFactory.create(
        scope = backgroundScope,
        produceFile = { File(tmp, name) },
    )

    private companion object {
        val KEY_BY_JWT = stringPreferencesKey("urnetwork_by_jwt")
        val KEY_BY_JWT_ENC = stringPreferencesKey("urnetwork_by_jwt_enc")
        val KEY_BY_CLIENT_JWT = stringPreferencesKey("urnetwork_by_client_jwt")
        val KEY_BY_CLIENT_JWT_ENC = stringPreferencesKey("urnetwork_by_client_jwt_enc")
        const val BY_JWT = "eyJ.by.jwt"
        const val BY_CLIENT = "eyJ.client.jwt"
    }
}
