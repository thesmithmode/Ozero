package ru.ozero.corebackup

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.first
import ru.ozero.corestorage.dao.AppSplitRuleDao
import ru.ozero.corestorage.entity.AppSplitRule
import ru.ozero.enginescore.settings.SettingsKeys
import ru.ozero.enginefptn.FptnConfigStore
import ru.ozero.engineurnetwork.UrnetworkConfigStore
import ru.ozero.engineurnetwork.UrnetworkLocationSelection
import ru.ozero.engineurnetwork.UrnetworkProvideControlMode
import ru.ozero.engineurnetwork.UrnetworkProvideNetworkMode
import ru.ozero.engineurnetwork.UrnetworkWindowType
import ru.ozero.enginewarp.AwgParams
import ru.ozero.enginewarp.WarpConfig
import ru.ozero.enginewarp.WarpConfigSlot
import ru.ozero.enginewarp.WarpConfigSlotStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AppBackupManager(
    private val ozeroSettings: DataStore<Preferences>,
    private val warpSlotStore: WarpConfigSlotStore,
    private val urnetworkStore: UrnetworkConfigStore,
    private val splitRuleDao: AppSplitRuleDao,
    private val fptnStore: FptnConfigStore? = null,
    private val strategyProvider: StrategyBackupProvider? = null,
) {

    suspend fun export(categories: Set<BackupCategory> = BackupCategory.ALL): AppBackupData {
        val prefs = ozeroSettings.data.first()
        val urnetwork = if (BackupCategory.URNETWORK in categories) exportUrnetwork() else BackupUrnetwork()
        val warpSlots =
            if (BackupCategory.WARP in categories) warpSlotStore.slots().first().map { it.toBackup() } else emptyList()
        val splitRules = if (BackupCategory.SPLIT_TUNNEL in categories) exportSplit() else emptyList()
        val strategy = if (BackupCategory.STRATEGY in categories) strategyProvider?.export() else null
        return AppBackupData(
            exportedAt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date()),
            settings = exportSettings(prefs, categories),
            urnetwork = urnetwork,
            warpSlots = warpSlots,
            splitRules = splitRules,
            strategy = strategy,
        )
    }

    private fun exportSettings(prefs: Preferences, categories: Set<BackupCategory>): BackupSettings {
        val inGeneral = BackupCategory.GENERAL_SETTINGS in categories
        val inByedpi = BackupCategory.BYEDPI in categories
        val inUrn = BackupCategory.URNETWORK in categories
        val inDns = BackupCategory.DNS_HOSTS in categories
        return BackupSettings(
            splitMode = if (inGeneral) prefs[SettingsKeys.SPLIT_MODE] else null,
            ipv6Enabled = if (inGeneral) prefs[SettingsKeys.IPV6_ENABLED] else null,
            autoStart = if (inGeneral) prefs[SettingsKeys.AUTO_START] else null,
            manualEngine = if (inGeneral) prefs[SettingsKeys.MANUAL_ENGINE] else null,
            bydpiWinningArgs = if (inByedpi) prefs[SettingsKeys.BYDPI_WINNING_ARGS] else null,
            urnetworkEnabled = if (inUrn) prefs[SettingsKeys.URNETWORK_ENABLED] else null,
            urnetworkJwt = if (inUrn) prefs[SettingsKeys.URNETWORK_JWT] else null,
            customDnsServers = if (inDns) prefs[SettingsKeys.CUSTOM_DNS_SERVERS] else null,
            hostsMode = if (inDns) prefs[SettingsKeys.HOSTS_MODE] else null,
            hostsList = if (inDns) prefs[SettingsKeys.HOSTS_LIST] else null,
            uiLocaleTag = if (inGeneral) prefs[SettingsKeys.UI_LOCALE_TAG] else null,
            appMode = if (inGeneral) prefs[SettingsKeys.APP_MODE] else null,
            engineAutoPriority = if (inGeneral) prefs[SettingsKeys.ENGINE_AUTO_PRIORITY] else null,
            bydpiUseUiMode = if (inByedpi) prefs[SettingsKeys.BYDPI_USE_UI_MODE] else null,
            bydpiUiSettingsJson = if (inByedpi) prefs[SettingsKeys.BYDPI_UI_SETTINGS_JSON] else null,
            bydpiDefaultAccepted = if (inByedpi) prefs[SettingsKeys.BYDPI_DEFAULT_ACCEPTED] else null,
            urnetworkCountryCode = if (inUrn) prefs[SettingsKeys.URNETWORK_COUNTRY_CODE] else null,
            fptnToken = if (inGeneral) fptnStore?.currentConfig()?.token?.takeIf { it.isNotBlank() } else null,
        )
    }

    private suspend fun exportUrnetwork(): BackupUrnetwork {
        val cfg = urnetworkStore.config().first()
        return BackupUrnetwork(
            byJwt = cfg.byJwt?.takeIf { it.isNotBlank() },
            byClientJwt = cfg.byClientJwt?.takeIf { it.isNotBlank() },
            devicePubkey = cfg.devicePubkey?.takeIf { it.isNotBlank() },
            deviceNetworkName = cfg.deviceNetworkName?.takeIf { it.isNotBlank() },
            windowType = cfg.windowType.rawValue,
            fixedIpSize = cfg.fixedIpSize,
            allowDirect = cfg.allowDirect,
            provideEnabled = cfg.provideEnabled,
            provideControlMode = cfg.provideControlMode.rawValue,
            provideNetworkMode = cfg.provideNetworkMode.rawValue,
            selectedLocation = cfg.selectedLocation.normalized()?.let {
                BackupUrnetworkLocation(countryCode = it.countryCode, region = it.region, city = it.city)
            },
        )
    }

    private suspend fun exportSplit(): List<BackupSplitRule> =
        splitRuleDao.observeAll().first().map {
            BackupSplitRule(packageName = it.packageName, isExcluded = it.isExcluded)
        }

    suspend fun import(data: AppBackupData, categories: Set<BackupCategory> = BackupCategory.ALL) {
        val importedSettings = data.settings
        ozeroSettings.edit { prefs ->
            if (BackupCategory.GENERAL_SETTINGS in categories) importGeneral(prefs, importedSettings)
            if (BackupCategory.BYEDPI in categories) importByedpi(prefs, importedSettings)
            if (BackupCategory.URNETWORK in categories) importUrnPrefs(prefs, importedSettings)
            if (BackupCategory.DNS_HOSTS in categories) importDns(prefs, importedSettings)
        }
        if (BackupCategory.GENERAL_SETTINGS in categories) importFptnToken(importedSettings)
        if (BackupCategory.URNETWORK in categories) importUrnConfig(data.urnetwork)
        if (BackupCategory.WARP in categories && data.warpSlots.isNotEmpty()) {
            warpSlotStore.replaceAll(data.warpSlots.map { it.toSlot() })
        }
        if (BackupCategory.STRATEGY in categories) data.strategy?.let { strategyProvider?.import(it) }
        if (BackupCategory.SPLIT_TUNNEL in categories && data.splitRules.isNotEmpty()) importSplit(data.splitRules)
    }

    private fun importGeneral(prefs: MutablePreferences, s: BackupSettings) {
        s.splitMode?.let { prefs[SettingsKeys.SPLIT_MODE] = it }
        s.ipv6Enabled?.let { prefs[SettingsKeys.IPV6_ENABLED] = it }
        s.autoStart?.let { prefs[SettingsKeys.AUTO_START] = it }
        s.manualEngine?.let { prefs[SettingsKeys.MANUAL_ENGINE] = it }
        s.uiLocaleTag?.let { prefs[SettingsKeys.UI_LOCALE_TAG] = it }
        s.appMode?.let { prefs[SettingsKeys.APP_MODE] = it }
        s.engineAutoPriority?.let { prefs[SettingsKeys.ENGINE_AUTO_PRIORITY] = it }
    }

    private suspend fun importFptnToken(s: BackupSettings) {
        val token = s.fptnToken?.takeIf { it.isNotBlank() } ?: return
        fptnStore?.update { it.copy(token = token) }
    }

    private fun importByedpi(prefs: MutablePreferences, s: BackupSettings) {
        s.bydpiWinningArgs?.let { prefs[SettingsKeys.BYDPI_WINNING_ARGS] = it }
        s.bydpiUseUiMode?.let { prefs[SettingsKeys.BYDPI_USE_UI_MODE] = it }
        s.bydpiUiSettingsJson?.let { prefs[SettingsKeys.BYDPI_UI_SETTINGS_JSON] = it }
        s.bydpiDefaultAccepted?.let { prefs[SettingsKeys.BYDPI_DEFAULT_ACCEPTED] = it }
    }

    private fun importUrnPrefs(prefs: MutablePreferences, s: BackupSettings) {
        s.urnetworkEnabled?.let { prefs[SettingsKeys.URNETWORK_ENABLED] = it }
        s.urnetworkJwt?.let { prefs[SettingsKeys.URNETWORK_JWT] = it }
        s.urnetworkCountryCode?.let { prefs[SettingsKeys.URNETWORK_COUNTRY_CODE] = it }
    }

    private fun importDns(prefs: MutablePreferences, s: BackupSettings) {
        s.customDnsServers?.let { prefs[SettingsKeys.CUSTOM_DNS_SERVERS] = it }
        s.hostsMode?.let { prefs[SettingsKeys.HOSTS_MODE] = it }
        s.hostsList?.let { prefs[SettingsKeys.HOSTS_LIST] = it }
    }

    private suspend fun importUrnConfig(u: BackupUrnetwork) {
        urnetworkStore.update { current ->
            current.copy(
                byJwt = u.byJwt ?: current.byJwt,
                byClientJwt = u.byClientJwt ?: current.byClientJwt,
                devicePubkey = u.devicePubkey ?: current.devicePubkey,
                deviceNetworkName = u.deviceNetworkName ?: current.deviceNetworkName,
                windowType = u.windowType?.let { UrnetworkWindowType.fromRaw(it) } ?: current.windowType,
                fixedIpSize = u.fixedIpSize ?: current.fixedIpSize,
                allowDirect = u.allowDirect ?: current.allowDirect,
                provideEnabled = u.provideEnabled ?: current.provideEnabled,
                provideControlMode = u.provideControlMode?.let { UrnetworkProvideControlMode.fromRaw(it) }
                    ?: current.provideControlMode,
                provideNetworkMode = u.provideNetworkMode?.let { UrnetworkProvideNetworkMode.fromRaw(it) }
                    ?: current.provideNetworkMode,
                selectedLocation = u.selectedLocation?.let {
                    UrnetworkLocationSelection(countryCode = it.countryCode, region = it.region, city = it.city)
                } ?: current.selectedLocation,
            )
        }
    }

    private suspend fun importSplit(rules: List<BackupSplitRule>) {
        val existingRules = splitRuleDao.observeAll().first()
        val backupPackages = rules.map { it.packageName }.toSet()
        for (existing in existingRules) {
            if (existing.packageName !in backupPackages) splitRuleDao.delete(existing.packageName)
        }
        for (rule in rules) {
            splitRuleDao.upsert(AppSplitRule(packageName = rule.packageName, isExcluded = rule.isExcluded))
        }
    }

    private fun WarpConfigSlot.toBackup(): BackupWarpSlot {
        val awg = config.awgParams
        return BackupWarpSlot(
            id = id,
            name = name,
            isActive = isActive,
            privateKey = config.privateKey,
            publicKey = config.publicKey,
            peerPublicKey = config.peerPublicKey,
            peerEndpoint = config.peerEndpoint,
            interfaceAddressV4 = config.interfaceAddressV4,
            interfaceAddressV6 = config.interfaceAddressV6,
            accountLicense = config.accountLicense,
            mtu = config.mtu,
            dnsServers = config.dnsServers,
            keepaliveSeconds = config.keepaliveSeconds,
            awgJc = awg.junkPacketCount,
            awgJmin = awg.junkPacketMinSize,
            awgJmax = awg.junkPacketMaxSize,
            awgS1 = awg.initPacketJunkSize,
            awgS2 = awg.responsePacketJunkSize,
            awgH1 = awg.initPacketMagicHeader,
            awgH2 = awg.responsePacketMagicHeader,
            awgH3 = awg.cookieReplyMagicHeader,
            awgH4 = awg.transportMagicHeader,
            awgS3 = awg.underloadPacketJunkSize,
            awgS4 = awg.payloadPacketJunkSize,
            awgI1 = awg.payloadPacketSizeCount1,
            awgI2 = awg.payloadPacketSizeCount2,
            awgI3 = awg.specialJunk3,
            awgI4 = awg.specialJunk4,
            awgI5 = awg.payloadPacketSizeCount3,
            awgI1Hex = awg.payloadHexI1,
            awgI2Hex = awg.payloadHexI2,
            awgI3Hex = awg.payloadHexI3,
            awgI4Hex = awg.payloadHexI4,
            awgI5Hex = awg.payloadHexI5,
        )
    }

    private fun BackupWarpSlot.toSlot(): WarpConfigSlot {
        return WarpConfigSlot(
            id = id,
            name = name,
            isActive = isActive,
            config = WarpConfig(
                privateKey = privateKey,
                publicKey = publicKey,
                peerPublicKey = peerPublicKey,
                peerEndpoint = peerEndpoint,
                interfaceAddressV4 = interfaceAddressV4,
                interfaceAddressV6 = interfaceAddressV6,
                accountLicense = accountLicense,
                mtu = mtu,
                dnsServers = dnsServers,
                keepaliveSeconds = keepaliveSeconds,
                awgParams = AwgParams(
                    junkPacketCount = awgJc,
                    junkPacketMinSize = awgJmin,
                    junkPacketMaxSize = awgJmax,
                    initPacketJunkSize = awgS1,
                    responsePacketJunkSize = awgS2,
                    initPacketMagicHeader = awgH1,
                    responsePacketMagicHeader = awgH2,
                    cookieReplyMagicHeader = awgH3,
                    transportMagicHeader = awgH4,
                    underloadPacketJunkSize = awgS3 ?: AwgParams.DEFAULT_S3,
                    payloadPacketJunkSize = awgS4 ?: AwgParams.DEFAULT_S4,
                    payloadPacketSizeCount1 = awgI1 ?: AwgParams.DEFAULT_I1,
                    payloadPacketSizeCount2 = awgI2 ?: AwgParams.DEFAULT_I2,
                    specialJunk3 = awgI3 ?: AwgParams.DEFAULT_I3,
                    specialJunk4 = awgI4 ?: AwgParams.DEFAULT_I4,
                    payloadPacketSizeCount3 = awgI5 ?: AwgParams.DEFAULT_I5,
                    payloadHexI1 = awgI1Hex,
                    payloadHexI2 = awgI2Hex,
                    payloadHexI3 = awgI3Hex,
                    payloadHexI4 = awgI4Hex,
                    payloadHexI5 = awgI5Hex,
                ),
            ),
        )
    }
}
