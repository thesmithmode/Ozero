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
) {
    fun fingerprint(): String? = profile?.let {
        val digest = MessageDigest.getInstance("SHA-256").digest(it.json.toByteArray())
        val hash = digest.joinToString("") { byte -> "%02x".format(byte) }
        "$hash:${transport.name}:$autoEnabled"
    }
}

class MasqueProfileStore(private val store: DataStore<Preferences>) {
    fun settings(): Flow<MasqueSettings> = store.data.map { prefs ->
        MasqueSettings(
            profile = prefs[PROFILE]?.let(::decode),
            transport = prefs[TRANSPORT]?.let(MasqueTransport::valueOf) ?: MasqueTransport.AUTO,
            autoEnabled = prefs[AUTO_ENABLED] ?: true,
        )
    }

    fun profiles(): Flow<MasqueProfile?> = settings().map { it.profile }

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
        store.edit { it.remove(PROFILE) }
    }

    private fun encrypt(profile: MasqueProfile): String {
        val sealed = PreferenceAtRest.seal(profile.json)
        check(PreferenceAtRest.isSealed(sealed)) { "MASQUE profile encryption failed" }
        return sealed
    }

    private fun decode(value: String): MasqueProfile {
        check(PreferenceAtRest.isSealed(value)) { "MASQUE profile is not encrypted" }
        return MasqueProfile.parse(PreferenceAtRest.open(value))
    }

    private companion object {
        val PROFILE = stringPreferencesKey("masque_profile")
        val TRANSPORT = stringPreferencesKey("masque_transport")
        val AUTO_ENABLED = booleanPreferencesKey("masque_auto_enabled")
    }
}
