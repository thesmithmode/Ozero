package ru.ozero.app.vpn

import androidx.datastore.preferences.core.Preferences
import ru.ozero.app.ui.settings.engines.singbox.SingboxProbeService
import ru.ozero.enginesingbox.SingboxEngine
import ru.ozero.singboxroom.entity.ProxyChainStep
import ru.ozero.singboxroom.entity.ProxyProfile

internal fun singboxRuntimeFingerprint(
    prefs: Preferences,
    profiles: List<ProxyProfile>,
    chainSteps: List<ProxyChainStep>,
): Any {
    val selectedProfileId = prefs[SingboxProbeService.SELECTED_PROFILE_KEY]
    if (selectedProfileId == SingboxEngine.SELECTED_AUTO) {
        val profileBlobHashes = profiles.map { it.id to it.beanBlob.contentHashCode() }
        return listOf(selectedProfileId, profileBlobHashes)
    }
    val profilesById = profiles.associateBy { it.id }
    val selectedBlobHash = selectedProfileId
        ?.let { profilesById[it]?.beanBlob?.contentHashCode() }
        ?: prefs[SingboxProbeService.BEAN_KEY]?.contentHashCode()
        ?: 0
    val activeProfileBlobHashes = chainSteps
        .map { it.profileId }
        .filter { it != selectedProfileId }
        .mapNotNull { id -> profilesById[id]?.let { id to it.beanBlob.contentHashCode() } }
    return listOf(selectedProfileId, selectedBlobHash, activeProfileBlobHashes)
}
