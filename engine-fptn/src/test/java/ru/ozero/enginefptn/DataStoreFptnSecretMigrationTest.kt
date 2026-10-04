package ru.ozero.enginefptn

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import ru.ozero.commoncrypto.AtRestCipher
import ru.ozero.commoncrypto.AtRestSecrets
import ru.ozero.commoncrypto.PreferenceAtRest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DataStoreFptnSecretMigrationTest {
    @TempDir
    lateinit var tmp: File

    @BeforeEach
    fun installCipher() {
        installDefaultAtRestCipher()
    }

    @Test
    fun `plaintext token is migrated and the old key is removed`() = runTest {
        val dataStore = newStore("migrate.preferences_pb")
        dataStore.edit { prefs ->
            prefs[stringPreferencesKey("fptn_token")] = PLAIN
        }
        val store = DataStoreFptnConfigStore(dataStore)

        val config = store.config().first()

        assertEquals(PLAIN, config.token)
        val prefs = dataStore.data.first()
        assertNull(prefs[stringPreferencesKey("fptn_token")])
        val sealed = prefs[stringPreferencesKey("fptn_token_enc")]
        assertTrue(sealed != null && sealed.startsWith("enc1:"))
        assertFalse(sealed.contains(PLAIN))
        assertEquals(PLAIN, PreferenceAtRest.open(sealed))
    }

    @Test
    fun `already encrypted token is read back as the original secret`() = runTest {
        installDefaultAtRestCipher()
        val sealed = PreferenceAtRest.seal(PLAIN)
        val dataStoreScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val dataStore = PreferenceDataStoreFactory.create(
            scope = dataStoreScope,
            produceFile = { File(tmp, "sealed.preferences_pb") },
        )
        try {
            dataStore.edit { prefs ->
                prefs[stringPreferencesKey("fptn_token_enc")] = sealed
            }
            val store = DataStoreFptnConfigStore(dataStore)

            assertEquals(PLAIN, store.currentConfig().token)
            assertEquals(PLAIN, store.config().first().token)
            assertEquals(sealed, dataStore.data.first()[stringPreferencesKey("fptn_token_enc")])
            assertNull(dataStore.data.first()[stringPreferencesKey("fptn_token")])
        } finally {
            dataStoreScope.cancel()
        }
    }

    @Test
    fun `second read does not double encrypt the token`() = runTest {
        val dataStore = newStore("twice.preferences_pb")
        dataStore.edit { prefs ->
            prefs[stringPreferencesKey("fptn_token")] = PLAIN
        }
        val store = DataStoreFptnConfigStore(dataStore)

        assertEquals(PLAIN, store.config().first().token)
        val first = dataStore.data.first()[stringPreferencesKey("fptn_token_enc")]
        assertEquals(PLAIN, store.currentConfig().token)
        val second = dataStore.data.first()[stringPreferencesKey("fptn_token_enc")]

        assertEquals(first, second)
        assertEquals(PLAIN, PreferenceAtRest.open(second!!))
    }

    @Test
    fun `failed encryption keeps the plaintext token`() = runTest {
        val dataStore = newStore("fail.preferences_pb")
        dataStore.edit { prefs ->
            prefs[stringPreferencesKey("fptn_token")] = PLAIN
        }
        AtRestSecrets.installCipher(object : AtRestCipher {
            override fun encrypt(plaintext: ByteArray): ByteArray = error("seal failed")
            override fun decrypt(payload: ByteArray): ByteArray = error("seal failed")
        })
        val store = DataStoreFptnConfigStore(dataStore)
        try {
            assertThrows<IllegalStateException> { store.config().first() }
            val prefs = dataStore.data.first()
            assertEquals(PLAIN, prefs[stringPreferencesKey("fptn_token")])
            assertNull(prefs[stringPreferencesKey("fptn_token_enc")])
        } finally {
            installDefaultAtRestCipher()
        }
    }

    private fun TestScope.newStore(name: String) = PreferenceDataStoreFactory.create(
        scope = backgroundScope,
        produceFile = { File(tmp, name) },
    )

    private companion object {
        const val PLAIN = "fptn:user-secret"
    }
}
