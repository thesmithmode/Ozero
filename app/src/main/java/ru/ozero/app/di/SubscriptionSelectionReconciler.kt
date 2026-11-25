package ru.ozero.app.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import ru.ozero.app.ui.settings.engines.singbox.SingboxProbeService
import ru.ozero.enginesingbox.SingboxEngine
import ru.ozero.singboxroom.dao.ProxyProfileDao

internal suspend fun reconcileSubscriptionSelection(dataStore: DataStore<Preferences>, profileDao: ProxyProfileDao) {
    dataStore.edit { prefs ->
        val selected = prefs[SingboxProbeService.SELECTED_PROFILE_KEY] ?: return@edit
        if (selected != SingboxEngine.SELECTED_AUTO && profileDao.getById(selected) == null) {
            prefs.remove(SingboxProbeService.SELECTED_PROFILE_KEY)
            prefs.remove(SingboxProbeService.BEAN_KEY)
        }
    }
}
