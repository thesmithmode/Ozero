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
    val selectedBlobHash = when {
        selectedProfileId == null -> prefs[SingboxProbeService.BEAN_KEY]?.contentHashCode() ?: 0
        selectedProfileId in profilesById -> profilesById.getValue(selectedProfileId).beanBlob.contentHashCode()
        else -> 0
    }
    val activeProfileBlobHashes = chainSteps
        .map { it.profileId }
        .filter { it != selectedProfileId }
        .mapNotNull { id -> profilesById[id]?.let { id to it.beanBlob.contentHashCode() } }
    return listOf(selectedProfileId, selectedBlobHash, activeProfileBlobHashes)
}

internal suspend fun singboxRuntimeFingerprint(
    prefs: Preferences,
    profiles: List<ProxyProfile>,
    chainSteps: List<ProxyChainStep>,
    resolveProfileById: suspend (Long) -> ProxyProfile?,
): Any {
    val selectedProfileId = prefs[SingboxProbeService.SELECTED_PROFILE_KEY]
    if (selectedProfileId == null || selectedProfileId == SingboxEngine.SELECTED_AUTO) {
        return singboxRuntimeFingerprint(prefs, profiles, chainSteps)
    }
    if (profiles.any { it.id == selectedProfileId }) {
        return singboxRuntimeFingerprint(prefs, profiles, chainSteps)
    }
    val resolvedProfile = resolveProfileById(selectedProfileId)
    val resolvedProfiles = if (resolvedProfile == null) profiles else profiles + resolvedProfile
    return singboxRuntimeFingerprint(prefs, resolvedProfiles, chainSteps)
}
