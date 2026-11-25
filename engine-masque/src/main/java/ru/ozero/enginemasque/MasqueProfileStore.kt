package ru.ozero.enginemasque

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ru.ozero.commoncrypto.PreferenceAtRest
import java.security.MessageDigest

data class MasqueSettings(
    val profile: MasqueProfile? = null,
    val transport: MasqueTransport = MasqueTransport.AUTO,
    val autoEnabled: Boolean = true,
    val corrupt: Boolean = false,
) {
    fun fingerprint(): String? = profile?.takeUnless { corrupt }?.let {
        val digest = MessageDigest.getInstance("SHA-256").digest(it.json.toByteArray())
        val hash = digest.joinToString("") { byte -> "%02x".format(byte) }
        "$hash:${transport.name}:$autoEnabled"
    }
}

class MasqueProfileStore(private val store: DataStore<Preferences>) {
    fun settings(): Flow<MasqueSettings> = store.data.map { prefs ->
        val raw = prefs[PROFILE]
        val rawTransport = prefs[TRANSPORT]
        val profile = raw?.let(::decodeOrNull)
        val transport = MasqueTransport.entries.firstOrNull { it.name == rawTransport }
        MasqueSettings(
            profile = profile,
            transport = transport ?: MasqueTransport.AUTO,
            autoEnabled = prefs[AUTO_ENABLED] ?: true,
            corrupt = (raw != null && profile == null) || (rawTransport != null && transport == null),
        )
    }

    fun profiles(): Flow<MasqueProfile?> = settings().map { it.profile.takeUnless { _ -> it.corrupt } }

    suspend fun save(profile: MasqueProfile) {
        val sealed = encrypt(profile)
        store.edit { it[PROFILE] = sealed }
    }

    suspend fun setTransport(transport: MasqueTransport) {
        store.edit { it[TRANSPORT] = transport.name }
    }

    suspend fun setAutoEnabled(enabled: Boolean) {
        store.edit { it[AUTO_ENABLED] = enabled }
    }

    suspend fun restore(profile: MasqueProfile?, transport: MasqueTransport, autoEnabled: Boolean) {
        val sealed = profile?.let(::encrypt)
        store.edit {
            sealed?.let { encrypted -> it[PROFILE] = encrypted }
            it[TRANSPORT] = transport.name
            it[AUTO_ENABLED] = autoEnabled
        }
    }

    suspend fun clear() {
        store.edit { prefs ->
            prefs.remove(PROFILE)
            if (MasqueTransport.entries.none { it.name == prefs[TRANSPORT] }) prefs.remove(TRANSPORT)
        }
    }

    private fun encrypt(profile: MasqueProfile): String {
        val sealed = PreferenceAtRest.seal(profile.json)
        check(PreferenceAtRest.isSealed(sealed)) { "MASQUE profile encryption failed" }
        return sealed
    }

    private fun decodeOrNull(value: String): MasqueProfile? {
        if (!PreferenceAtRest.isSealed(value)) return null
        return try {
            MasqueProfile.parse(PreferenceAtRest.open(value))
        } catch (_: Exception) {
            null
        }
    }

    private companion object {
        val PROFILE = stringPreferencesKey("masque_profile")
        val TRANSPORT = stringPreferencesKey("masque_transport")
        val AUTO_ENABLED = booleanPreferencesKey("masque_auto_enabled")
    }
}
