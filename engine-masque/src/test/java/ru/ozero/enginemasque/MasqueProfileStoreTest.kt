package ru.ozero.enginemasque

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
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
        assertThrows<IllegalStateException> { store.profiles().first() }
        data.edit { it[KEY] = "enc1:AAAA" }
        assertThrows<IllegalArgumentException> { store.profiles().first() }
    }

    private companion object {
        val KEY = stringPreferencesKey("masque_profile")
        val PROFILE = """{"private_key":"test-private-key","endpoint_pub_key":"test","endpoint_v4":"192.0.2.1",
            |"ipv4":"192.0.2.2","ipv6":"2001:db8::1","id":"test","access_token":"test"}
        """.trimMargin()
    }
}
