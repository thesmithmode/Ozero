package ru.ozero.app.vpn

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringSetPreferencesKey
import ru.ozero.app.ui.settings.engines.singbox.SingboxProbeService
import ru.ozero.enginesingbox.SingboxEngine
import ru.ozero.enginesingbox.prioritizeSingboxAutoProfiles
import ru.ozero.singboxconfig.ConfigBuilder
import ru.ozero.singboxfmt.AbstractBean
import ru.ozero.singboxfmt.KryoSerializer
import ru.ozero.singboxroom.entity.ProxyChainStep
import ru.ozero.singboxroom.entity.ProxyProfile

internal fun singboxRuntimeFingerprint(
    prefs: Preferences,
    profiles: List<ProxyProfile>,
    chainSteps: List<ProxyChainStep>,
): Any {
    val selectedProfileId = prefs[SingboxProbeService.SELECTED_PROFILE_KEY]
    val dnsFingerprint = prefs[SINGBOX_DNS_SERVERS_KEY]?.toList()?.sorted().orEmpty()
    if (selectedProfileId == SingboxEngine.SELECTED_AUTO) {
        val supportedProfiles = profiles
            .asSequence()
            .take(MAX_AUTO_SELECT_FINGERPRINT_SCAN)
            .filter { isSupportedRoutableBlob(it.beanBlob) }
            .toList()
        val profileBlobHashes = prioritizeSingboxAutoProfiles(
            supportedProfiles,
            MAX_AUTO_SELECT_FINGERPRINT_PROFILES,
        )
            .asSequence()
            .map { profile -> profile.id to profile.beanBlob.contentHashCode() }
            .toList()
        return listOf(selectedProfileId, profileBlobHashes, dnsFingerprint)
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
    return listOf(selectedProfileId, selectedBlobHash, activeProfileBlobHashes, dnsFingerprint)
}

private val SINGBOX_DNS_SERVERS_KEY = stringSetPreferencesKey("singbox_dns_servers")

private const val MAX_AUTO_SELECT_FINGERPRINT_SCAN = 2_000
private const val MAX_AUTO_SELECT_FINGERPRINT_PROFILES = 50

private fun isSupportedRoutableBlob(blob: ByteArray): Boolean =
    runCatching { KryoSerializer.deserialize<AbstractBean>(blob) }
        .getOrNull()
        ?.let { ConfigBuilder.isSupportedBean(it) && it.hasRoutableServerAddress() }
        ?: false

private fun AbstractBean.hasRoutableServerAddress(): Boolean {
    val host = serverAddress.trim().trim('[', ']').lowercase()
    return host.isNotEmpty() &&
        host != "localhost" &&
        host != "0.0.0.0" &&
        host != "::" &&
        host != "::0" &&
        host != "::1" &&
        !host.startsWith("127.")
}

internal suspend fun singboxRuntimeFingerprint(
    prefs: Preferences,
    profiles: List<ProxyProfile>,
    chainSteps: List<ProxyChainStep>,
    resolveProfileById: suspend (Long) -> ProxyProfile?,
): Any {
    val selectedProfileId = prefs[SingboxProbeService.SELECTED_PROFILE_KEY]
    if (selectedProfileId == SingboxEngine.SELECTED_AUTO) {
        return singboxRuntimeFingerprint(prefs, profiles, chainSteps)
    }
    val profilesById = profiles.associateBy { it.id }
    val missingProfileIds = buildList {
        if (selectedProfileId != null && selectedProfileId !in profilesById) add(selectedProfileId)
        chainSteps
            .map { it.profileId }
            .filter { it !in profilesById && it != selectedProfileId }
            .distinct()
            .forEach(::add)
    }
    if (missingProfileIds.isEmpty()) {
        return singboxRuntimeFingerprint(prefs, profiles, chainSteps)
    }
    val resolvedProfiles = profiles + missingProfileIds.mapNotNull { id -> resolveProfileById(id) }
    return singboxRuntimeFingerprint(prefs, resolvedProfiles, chainSteps)
}
