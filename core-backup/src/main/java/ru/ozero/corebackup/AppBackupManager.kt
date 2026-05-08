package ru.ozero.corebackup

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.first
import ru.ozero.corestorage.dao.AppSplitRuleDao
import ru.ozero.corestorage.entity.AppSplitRule
import ru.ozero.enginescore.settings.SettingsKeys
import ru.ozero.engineurnetwork.UrnetworkConfigStore
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
) {

    suspend fun export(): AppBackupData {
        val prefs = ozeroSettings.data.first()

        val settings = BackupSettings(
            splitMode = prefs[SettingsKeys.SPLIT_MODE],
            ipv6Enabled = prefs[SettingsKeys.IPV6_ENABLED],
            autoStart = prefs[SettingsKeys.AUTO_START],
            manualEngine = prefs[SettingsKeys.MANUAL_ENGINE],
            bydpiWinningArgs = prefs[SettingsKeys.BYDPI_WINNING_ARGS],
            urnetworkEnabled = prefs[SettingsKeys.URNETWORK_ENABLED],
            urnetworkJwt = prefs[SettingsKeys.URNETWORK_JWT],
            customDnsServers = prefs[SettingsKeys.CUSTOM_DNS_SERVERS],
            hostsMode = prefs[SettingsKeys.HOSTS_MODE],
            hostsList = prefs[SettingsKeys.HOSTS_LIST],
            uiLocaleTag = prefs[SettingsKeys.UI_LOCALE_TAG],
            appMode = prefs[SettingsKeys.APP_MODE],
        )

        val urnetwork = BackupUrnetwork(
            walletOverride = urnetworkStore.walletOverride().first(),
            byJwt = urnetworkStore.byJwt().first(),
        )

        val warpSlots = warpSlotStore.slots().first().map { it.toBackup() }

        val splitRules = splitRuleDao.observeAll().first().map { rule ->
            BackupSplitRule(packageName = rule.packageName, isExcluded = rule.isExcluded)
        }

        return AppBackupData(
            exportedAt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date()),
            settings = settings,
            urnetwork = urnetwork,
            warpSlots = warpSlots,
            splitRules = splitRules,
        )
    }

    suspend fun import(data: AppBackupData) {
        ozeroSettings.edit { prefs ->
            val s = data.settings
            s.splitMode?.let { prefs[SettingsKeys.SPLIT_MODE] = it }
            s.ipv6Enabled?.let { prefs[SettingsKeys.IPV6_ENABLED] = it }
            s.autoStart?.let { prefs[SettingsKeys.AUTO_START] = it }
            s.manualEngine?.let { prefs[SettingsKeys.MANUAL_ENGINE] = it }
            s.bydpiWinningArgs?.let { prefs[SettingsKeys.BYDPI_WINNING_ARGS] = it }
            s.urnetworkEnabled?.let { prefs[SettingsKeys.URNETWORK_ENABLED] = it }
            s.urnetworkJwt?.let { prefs[SettingsKeys.URNETWORK_JWT] = it }
            s.customDnsServers?.let { prefs[SettingsKeys.CUSTOM_DNS_SERVERS] = it }
            s.hostsMode?.let { prefs[SettingsKeys.HOSTS_MODE] = it }
            s.hostsList?.let { prefs[SettingsKeys.HOSTS_LIST] = it }
            s.uiLocaleTag?.let { prefs[SettingsKeys.UI_LOCALE_TAG] = it }
            s.appMode?.let { prefs[SettingsKeys.APP_MODE] = it }
        }

        data.urnetwork.walletOverride?.let { urnetworkStore.setWalletOverride(it) }
        data.urnetwork.byJwt?.let { urnetworkStore.setByJwt(it) }

        if (data.warpSlots.isNotEmpty()) {
            warpSlotStore.replaceAll(data.warpSlots.map { it.toSlot() })
        }

        if (data.splitRules.isNotEmpty()) {
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
                ),
            ),
        )
    }
}
