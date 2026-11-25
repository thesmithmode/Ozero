package ru.ozero.enginemasque

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.ozero.commoncrypto.AesGcmAtRestCipher
import ru.ozero.commoncrypto.AtRestSecrets
import java.nio.file.Path
import javax.crypto.spec.SecretKeySpec

class MasqueProfileStoreTest {
    @BeforeEach
    fun installKey() {
        AtRestSecrets.installCipher(AesGcmAtRestCipher(SecretKeySpec(ByteArray(32) { it.toByte() }, "AES")))
    }

    @Test
    fun `profile is encrypted in datastore and clears on request`(@TempDir tmp: Path) = runTest {
        val data = PreferenceDataStoreFactory.create(
            produceFile = { tmp.resolve("masque.preferences_pb").toFile() },
        )
        val store = MasqueProfileStore(data)
        assertNull(store.profiles().first())
        store.save(MasqueProfile.parse(PROFILE))
        val stored = data.data.first()[KEY].orEmpty()
        assertTrue(stored.startsWith(AtRestSecrets.PREFIX))
        assertFalse(stored.contains("test-private-key"))
        assertEquals(PROFILE, store.profiles().first()?.json)
        store.clear()
        assertNull(store.profiles().first())
        assertNull(data.data.first()[KEY])
    }

    @Test
    fun `unencrypted and corrupted preferences fail closed`(@TempDir tmp: Path) = runTest {
        val data = PreferenceDataStoreFactory.create(
            produceFile = { tmp.resolve("masque.preferences_pb").toFile() },
        )
        val store = MasqueProfileStore(data)
        data.edit { it[KEY] = PROFILE }
        assertTrue(store.settings().first().corrupt)
        assertNull(store.profiles().first())
        data.edit { it[KEY] = "enc1:AAAA" }
        assertTrue(store.settings().first().corrupt)
        assertNull(store.profiles().first())
        store.clear()
        assertFalse(store.settings().first().corrupt)
        assertNull(store.profiles().first())
    }

    @Test
    fun `transport auto eligibility and profile restore are persistent`(@TempDir tmp: Path) = runTest {
        val data = PreferenceDataStoreFactory.create(
            produceFile = { tmp.resolve("masque.preferences_pb").toFile() },
        )
        val store = MasqueProfileStore(data)
        assertEquals(MasqueTransport.AUTO, store.settings().first().transport)
        assertTrue(store.settings().first().autoEnabled)
        assertNull(store.settings().first().fingerprint())
        val profile = MasqueProfile.parse(PROFILE)
        store.restore(profile, MasqueTransport.H2, false)
        val first = store.settings().first()
        assertEquals(MasqueTransport.H2, first.transport)
        assertFalse(first.autoEnabled)
        assertEquals(PROFILE, first.profile?.exportJson())
        store.setTransport(MasqueTransport.H3)
        store.setAutoEnabled(true)
        val updated = store.settings().first()
        assertEquals(MasqueTransport.H3, updated.transport)
        assertTrue(updated.autoEnabled)
        assertFalse(first.fingerprint() == updated.fingerprint())
        store.restore(null, MasqueTransport.AUTO, true)
        assertEquals(PROFILE, store.profiles().first()?.exportJson())
        store.clear()
        assertNull(store.settings().first().fingerprint())
    }

    @Test
    fun `invalid transport fails closed even with an encrypted valid profile`(@TempDir tmp: Path) = runTest {
        val data = PreferenceDataStoreFactory.create(
            produceFile = { tmp.resolve("masque.preferences_pb").toFile() },
        )
        val store = MasqueProfileStore(data)
        store.save(MasqueProfile.parse(PROFILE))
        val key = stringPreferencesKey("masque_transport")
        data.edit { it[key] = "invalid" }
        val damaged = store.settings().first()
        assertTrue(damaged.corrupt)
        assertNull(damaged.fingerprint())
        assertNull(store.profiles().first())
        store.clear()
        assertFalse(store.settings().first().corrupt)
        assertNull(data.data.first()[key])
    }

    @Test
    fun `clearing credentials preserves valid transport settings`(@TempDir tmp: Path) = runTest {
        val data = PreferenceDataStoreFactory.create(
            produceFile = { tmp.resolve("masque.preferences_pb").toFile() },
        )
        val store = MasqueProfileStore(data)
        store.restore(MasqueProfile.parse(PROFILE), MasqueTransport.H3, false)
        store.clear()
        val settings = store.settings().first()
        assertNull(settings.profile)
        assertEquals(MasqueTransport.H3, settings.transport)
        assertFalse(settings.autoEnabled)
        assertFalse(settings.corrupt)
    }

    @Test
    fun `observing profile updates emits the registered credentials and clear state`(@TempDir tmp: Path) = runTest {
        val data = PreferenceDataStoreFactory.create(
            produceFile = { tmp.resolve("masque.preferences_pb").toFile() },
        )
        val store = MasqueProfileStore(data)
        val initial = CompletableDeferred<Unit>()
        val registered = CompletableDeferred<Unit>()
        val values = mutableListOf<String?>()
        val collector = launch {
            store.profiles().take(3).collect { profile ->
                values.add(profile?.exportJson())
                if (values.size == 1) initial.complete(Unit)
                if (values.size == 2) registered.complete(Unit)
            }
        }
        initial.await()
        store.save(MasqueProfile.parse(PROFILE))
        registered.await()
        store.clear()
        collector.join()
        assertEquals(listOf(null, PROFILE, null), values)
    }

    @Test
    fun `immediate storage updates preserve profile and settings without suspension`() = runTest {
        val data = ImmediatePreferencesStore()
        val store = MasqueProfileStore(data)
        val profile = MasqueProfile.parse(PROFILE)
        store.save(profile)
        assertEquals(PROFILE, store.profiles().first()?.exportJson())
        store.setTransport(MasqueTransport.H2)
        store.setAutoEnabled(false)
        assertEquals(MasqueTransport.H2, store.settings().first().transport)
        assertFalse(store.settings().first().autoEnabled)
        store.restore(profile, MasqueTransport.H3, true)
        assertEquals(MasqueTransport.H3, store.settings().first().transport)
        assertTrue(store.settings().first().autoEnabled)
        store.restore(null, MasqueTransport.AUTO, false)
        assertEquals(PROFILE, store.profiles().first()?.exportJson())
        store.clear()
        assertNull(store.profiles().first())
        assertFalse(store.settings().first().corrupt)
    }

    private class ImmediatePreferencesStore : DataStore<Preferences> {
        private val state = MutableStateFlow<Preferences>(emptyPreferences())
        override val data: Flow<Preferences> get() = state

        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
            val updated = transform(state.value)
            state.value = updated
            return updated
        }
    }

    private companion object {
        val KEY = stringPreferencesKey("masque_profile")
        val PROFILE = """{"private_key":"test-private-key","endpoint_pub_key":"test","endpoint_v4":"192.0.2.1",
            |"ipv4":"192.0.2.2","ipv6":"2001:db8::1","id":"test","access_token":"test"}
        """.trimMargin()
    }
}
