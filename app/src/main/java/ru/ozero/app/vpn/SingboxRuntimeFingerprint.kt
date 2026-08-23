package ru.ozero.app.vpn

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringSetPreferencesKey
import ru.ozero.app.ui.settings.engines.singbox.SingboxProbeService
import ru.ozero.enginesingbox.SingboxEngine
import ru.ozero.enginesingbox.prioritizeSingboxAutoProfiles
import ru.ozero.singboxconfig.BeanSupportDecision
import ru.ozero.singboxconfig.ConfigBuilder
import ru.ozero.singboxconfig.PersistedProfileRecovery
import ru.ozero.singboxconfig.RecoveryResult
import ru.ozero.singboxroom.entity.ProxyChainStep
import ru.ozero.singboxroom.entity.ProxyProfile

internal data class SingboxRuntimeFingerprint(
    val selectedProfileId: Long?,
    val selectedProfile: RuntimeProfilePayload?,
    val autoSelectProfiles: List<RuntimeProfilePayload>,
    val chainProfiles: List<RuntimeChainPayload>,
    val dnsServers: List<String>,
    val ipv6Enabled: Boolean,
)

internal data class RuntimeProfilePayload(
    val id: Long?,
    val protocolType: Int?,
    val beanPayload: List<Byte>,
)

internal data class RuntimeChainPayload(
    val profileId: Long,
    val profile: RuntimeProfilePayload?,
)

internal fun singboxRuntimeFingerprint(
    prefs: Preferences,
    profiles: List<ProxyProfile>,
    chainSteps: List<ProxyChainStep>,
    autoProfiles: List<ProxyProfile> = profiles,
    ipv6Enabled: Boolean = false,
): SingboxRuntimeFingerprint {
    val selectedProfileId = prefs[SingboxProbeService.SELECTED_PROFILE_KEY]
    val profilesById = profiles.associateBy { it.id }
    val dnsServers = prefs[SINGBOX_DNS_SERVERS_KEY]?.toList()?.sorted().orEmpty()
    val selectedProfile = when (selectedProfileId) {
        null -> prefs[SingboxProbeService.BEAN_KEY]?.let { RuntimeProfilePayload(null, null, it.toList()) }
        SingboxEngine.SELECTED_AUTO -> null
        else -> profilesById[selectedProfileId]?.toRuntimePayload()
    }
    val autoSelectProfiles = if (selectedProfileId == SingboxEngine.SELECTED_AUTO) {
        autoProfiles
            .asSequence()
            .take(MAX_AUTO_SELECT_FINGERPRINT_SCAN)
            .filter(::isSupportedRoutableProfile)
            .toList()
            .let { prioritizeSingboxAutoProfiles(it, MAX_AUTO_SELECT_FINGERPRINT_PROFILES) }
            .map(ProxyProfile::toRuntimePayload)
    } else {
        emptyList()
    }
    val chainProfiles = if (selectedProfileId == SingboxEngine.SELECTED_AUTO) {
        emptyList()
    } else {
        chainSteps
            .asSequence()
            .map { it.profileId }
            .filter { it != selectedProfileId }
            .map { id -> RuntimeChainPayload(id, profilesById[id]?.toRuntimePayload()) }
            .toList()
    }
    return SingboxRuntimeFingerprint(
        selectedProfileId = selectedProfileId,
        selectedProfile = selectedProfile,
        autoSelectProfiles = autoSelectProfiles,
        chainProfiles = chainProfiles,
        dnsServers = dnsServers,
        ipv6Enabled = ipv6Enabled,
    )
}

private fun ProxyProfile.toRuntimePayload(): RuntimeProfilePayload =
    RuntimeProfilePayload(id = id, protocolType = protocolType, beanPayload = beanBlob.toList())

private val SINGBOX_DNS_SERVERS_KEY = stringSetPreferencesKey("singbox_dns_servers")

private const val MAX_AUTO_SELECT_FINGERPRINT_SCAN = 2_000
private const val MAX_AUTO_SELECT_FINGERPRINT_PROFILES = 50

private fun isSupportedRoutableProfile(profile: ProxyProfile): Boolean =
    (PersistedProfileRecovery.recover(profile.beanBlob, profile.protocolType) as? RecoveryResult.Success)
        ?.bean
        ?.let { ConfigBuilder.supportDecision(it) is BeanSupportDecision.Supported }
        ?: false

internal suspend fun singboxRuntimeFingerprint(
    prefs: Preferences,
    profiles: List<ProxyProfile>,
    chainSteps: List<ProxyChainStep>,
    resolveProfileById: suspend (Long) -> ProxyProfile?,
    autoProfiles: List<ProxyProfile> = profiles,
    ipv6Enabled: Boolean = false,
): SingboxRuntimeFingerprint {
    val selectedProfileId = prefs[SingboxProbeService.SELECTED_PROFILE_KEY]
    if (selectedProfileId == SingboxEngine.SELECTED_AUTO) {
        return singboxRuntimeFingerprint(prefs, profiles, chainSteps, autoProfiles, ipv6Enabled)
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
        return singboxRuntimeFingerprint(prefs, profiles, chainSteps, autoProfiles, ipv6Enabled)
    }
    val resolvedProfiles = profiles + buildList {
        missingProfileIds.forEach { profileId ->
            resolveProfileById(profileId)?.let(::add)
        }
    }
    return singboxRuntimeFingerprint(prefs, resolvedProfiles, chainSteps, autoProfiles, ipv6Enabled)
}
