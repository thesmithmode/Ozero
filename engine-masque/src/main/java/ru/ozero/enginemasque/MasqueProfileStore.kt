package ru.ozero.enginemasque

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ru.ozero.commoncrypto.PreferenceAtRest

class MasqueProfileStore(private val store: DataStore<Preferences>) {
    fun profiles(): Flow<MasqueProfile?> = store.data.map { prefs ->
        prefs[PROFILE]?.let(::decode)
    }

    suspend fun save(profile: MasqueProfile) {
        val sealed = PreferenceAtRest.seal(profile.json)
        check(PreferenceAtRest.isSealed(sealed)) { "MASQUE profile encryption failed" }
        store.edit { it[PROFILE] = sealed }
    }

    suspend fun clear() {
        store.edit { it.remove(PROFILE) }
    }

    private fun decode(value: String): MasqueProfile {
        check(PreferenceAtRest.isSealed(value)) { "MASQUE profile is not encrypted" }
        return MasqueProfile.parse(PreferenceAtRest.open(value))
    }

    private companion object {
        val PROFILE = stringPreferencesKey("masque_profile")
    }
}
