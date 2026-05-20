package ru.ozero.corebackup

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.first
import ru.ozero.corestorage.dao.AppSplitRuleDao
import ru.ozero.corestorage.entity.AppSplitRule
import ru.ozero.enginescore.settings.SettingsKeys
import ru.ozero.enginetelegram.TelegramConfigStore
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
    private val telegramStore: TelegramConfigStore,
    private val strategyProvider: StrategyBackupProvider? = null,
) {

    suspend fun export(categories: Set<BackupCategory> = BackupCategory.ALL): AppBackupData {
        val prefs = ozeroSettings.data.first()

        val settings = BackupSettings(
            splitMode = ifIn(categories, BackupCategory.GENERAL_SETTINGS) { prefs[SettingsKeys.SPLIT_MODE] },
            ipv6Enabled = ifIn(categories, BackupCategory.GENERAL_SETTINGS) { prefs[SettingsKeys.IPV6_ENABLED] },
            autoStart = ifIn(categories, BackupCategory.GENERAL_SETTINGS) { prefs[SettingsKeys.AUTO_START] },
            manualEngine = ifIn(categories, BackupCategory.GENERAL_SETTINGS) { prefs[SettingsKeys.MANUAL_ENGINE] },
            bydpiWinningArgs = ifIn(categories, BackupCategory.BYEDPI) { prefs[SettingsKeys.BYDPI_WINNING_ARGS] },
            urnetworkEnabled = ifIn(categories, BackupCategory.URNETWORK) { prefs[SettingsKeys.URNETWORK_ENABLED] },
            urnetworkJwt = ifIn(categories, BackupCategory.URNETWORK) { prefs[SettingsKeys.URNETWORK_JWT] },
            customDnsServers = ifIn(categories, BackupCategory.DNS_HOSTS) { prefs[SettingsKeys.CUSTOM_DNS_SERVERS] },
            hostsMode = ifIn(categories, BackupCategory.DNS_HOSTS) { prefs[SettingsKeys.HOSTS_MODE] },
            hostsList = ifIn(categories, BackupCategory.DNS_HOSTS) { prefs[SettingsKeys.HOSTS_LIST] },
            uiLocaleTag = ifIn(categories, BackupCategory.GENERAL_SETTINGS) { prefs[SettingsKeys.UI_LOCALE_TAG] },
            appMode = ifIn(categories, BackupCategory.GENERAL_SETTINGS) { prefs[SettingsKeys.APP_MODE] },
            engineAutoPriority = ifIn(categories, BackupCategory.GENERAL_SETTINGS) {
                prefs[SettingsKeys.ENGINE_AUTO_PRIORITY]
            },
            bydpiUseUiMode = ifIn(categories, BackupCategory.BYEDPI) { prefs[SettingsKeys.BYDPI_USE_UI_MODE] },
            bydpiUiSettingsJson = ifIn(categories, BackupCategory.BYEDPI) {
                prefs[SettingsKeys.BYDPI_UI_SETTINGS_JSON]
            },
            bydpiDefaultAccepted = ifIn(categories, BackupCategory.BYEDPI) {
                prefs[SettingsKeys.BYDPI_DEFAULT_ACCEPTED]
            },
            urnetworkCountryCode = ifIn(categories, BackupCategory.URNETWORK) {
                prefs[SettingsKeys.URNETWORK_COUNTRY_CODE]
            },
        )

        val urnetwork = if (BackupCategory.URNETWORK in categories) {
            val cfg = urnetworkStore.config().first()
            BackupUrnetwork(
                byJwt = cfg.byJwt?.takeIf { it.isNotBlank() },
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
        } else {
            BackupUrnetwork()
        }

        val warpSlots = if (BackupCategory.WARP in categories) {
            warpSlotStore.slots().first().map { it.toBackup() }
        } else {
            emptyList()
        }

        val splitRules = if (BackupCategory.SPLIT_TUNNEL in categories) {
            splitRuleDao.observeAll().first().map { rule ->
                BackupSplitRule(packageName = rule.packageName, isExcluded = rule.isExcluded)
            }
        } else {
            emptyList()
        }

        val strategy = if (BackupCategory.STRATEGY in categories) strategyProvider?.export() else null

        val telegram = if (BackupCategory.TELEGRAM in categories) {
            val tg = telegramStore.config().first()
            BackupTelegram(
                enabled = tg.enabled,
                port = tg.port,
                domain = tg.domain.takeIf { it.isNotBlank() },
                secret = tg.secret.takeIf { it.isNotBlank() },
            )
        } else {
            null
        }

        return AppBackupData(
            exportedAt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date()),
            settings = settings,
            urnetwork = urnetwork,
            warpSlots = warpSlots,
            splitRules = splitRules,
            strategy = strategy,
            telegram = telegram,
        )
    }

    suspend fun import(data: AppBackupData, categories: Set<BackupCategory> = BackupCategory.ALL) {
        ozeroSettings.edit { prefs ->
            val s = data.settings
            if (BackupCategory.GENERAL_SETTINGS in categories) {
                s.splitMode?.let { prefs[SettingsKeys.SPLIT_MODE] = it }
                s.ipv6Enabled?.let { prefs[SettingsKeys.IPV6_ENABLED] = it }
                s.autoStart?.let { prefs[SettingsKeys.AUTO_START] = it }
                s.manualEngine?.let { prefs[SettingsKeys.MANUAL_ENGINE] = it }
                s.uiLocaleTag?.let { prefs[SettingsKeys.UI_LOCALE_TAG] = it }
                s.appMode?.let { prefs[SettingsKeys.APP_MODE] = it }
                s.engineAutoPriority?.let { prefs[SettingsKeys.ENGINE_AUTO_PRIORITY] = it }
            }
            if (BackupCategory.BYEDPI in categories) {
                s.bydpiWinningArgs?.let { prefs[SettingsKeys.BYDPI_WINNING_ARGS] = it }
                s.bydpiUseUiMode?.let { prefs[SettingsKeys.BYDPI_USE_UI_MODE] = it }
                s.bydpiUiSettingsJson?.let { prefs[SettingsKeys.BYDPI_UI_SETTINGS_JSON] = it }
                s.bydpiDefaultAccepted?.let { prefs[SettingsKeys.BYDPI_DEFAULT_ACCEPTED] = it }
            }
            if (BackupCategory.URNETWORK in categories) {
                s.urnetworkEnabled?.let { prefs[SettingsKeys.URNETWORK_ENABLED] = it }
                s.urnetworkJwt?.let { prefs[SettingsKeys.URNETWORK_JWT] = it }
                s.urnetworkCountryCode?.let { prefs[SettingsKeys.URNETWORK_COUNTRY_CODE] = it }
            }
            if (BackupCategory.DNS_HOSTS in categories) {
                s.customDnsServers?.let { prefs[SettingsKeys.CUSTOM_DNS_SERVERS] = it }
                s.hostsMode?.let { prefs[SettingsKeys.HOSTS_MODE] = it }
                s.hostsList?.let { prefs[SettingsKeys.HOSTS_LIST] = it }
            }
        }

        if (BackupCategory.URNETWORK in categories) {
            val u = data.urnetwork
            urnetworkStore.update { current ->
                current.copy(
                    byJwt = u.byJwt?.takeIf { it.isNotBlank() } ?: current.byJwt,
                    windowType = u.windowType?.let { UrnetworkWindowType.fromRaw(it) } ?: current.windowType,
                    fixedIpSize = u.fixedIpSize ?: current.fixedIpSize,
                    allowDirect = u.allowDirect ?: current.allowDirect,
                    provideEnabled = u.provideEnabled ?: current.provideEnabled,
                    provideControlMode = u.provideControlMode?.let { UrnetworkProvideControlMode.fromRaw(it) }
                        ?: current.provideControlMode,
                    provideNetworkMode = u.provideNetworkMode?.let { UrnetworkProvideNetworkMode.fromRaw(it) }
                        ?: current.provideNetworkMode,
                    selectedLocation = u.selectedLocation?.let {
                        UrnetworkLocationSelection(
                            countryCode = it.countryCode,
                            region = it.region,
                            city = it.city,
                        )
                    } ?: current.selectedLocation,
                )
            }
        }

        if (BackupCategory.WARP in categories && data.warpSlots.isNotEmpty()) {
            warpSlotStore.replaceAll(data.warpSlots.map { it.toSlot() })
        }

        if (BackupCategory.STRATEGY in categories) {
            data.strategy?.let { strategyProvider?.import(it) }
        }

        if (BackupCategory.TELEGRAM in categories) {
            data.telegram?.let { tg ->
                tg.enabled?.let { telegramStore.setEnabled(it) }
                tg.port?.let { telegramStore.setPort(it) }
                tg.domain?.let { telegramStore.setDomain(it) }
                tg.secret?.let { telegramStore.setSecret(it) }
            }
        }

        if (BackupCategory.SPLIT_TUNNEL in categories && data.splitRules.isNotEmpty()) {
            val existingRules = splitRuleDao.observeAll().first()
            val backupPackages = data.splitRules.map { it.packageName }.toSet()
            for (existing in existingRules) {
                if (existing.packageName !in backupPackages) {
                    splitRuleDao.delete(existing.packageName)
                }
            }
            for (rule in data.splitRules) {
                splitRuleDao.upsert(AppSplitRule(packageName = rule.packageName, isExcluded = rule.isExcluded))
            }
        }
    }

    private inline fun <T> ifIn(categories: Set<BackupCategory>, cat: BackupCategory, block: () -> T?): T? =
        if (cat in categories) block() else null

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
                ),
            ),
        )
    }
}
