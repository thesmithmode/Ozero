package ru.ozero.corebackup

data class AppBackupData(
    val version: Int = CURRENT_VERSION,
    val exportedAt: String,
    val settings: BackupSettings,
    val urnetwork: BackupUrnetwork,
    val warpSlots: List<BackupWarpSlot>,
    val splitRules: List<BackupSplitRule>,
) {
    companion object {
        const val CURRENT_VERSION = 1
    }
}

data class BackupSettings(
    val splitMode: String?,
    val ipv6Enabled: Boolean?,
    val autoStart: Boolean?,
    val manualEngine: String?,
    val bydpiWinningArgs: String?,
    val urnetworkEnabled: Boolean?,
    val urnetworkJwt: String?,
    val customDnsServers: String?,
    val hostsMode: String?,
    val hostsList: String?,
    val uiLocaleTag: String?,
    val appMode: String?,
)

data class BackupUrnetwork(
    val walletOverride: String?,
    val byJwt: String?,
)

data class BackupWarpSlot(
    val id: String,
    val name: String,
    val isActive: Boolean,
    val privateKey: String,
    val publicKey: String,
    val peerPublicKey: String,
    val peerEndpoint: String,
    val interfaceAddressV4: String,
    val interfaceAddressV6: String,
    val accountLicense: String,
    val mtu: Int,
    val dnsServers: List<String>,
    val keepaliveSeconds: Int,
    val awgJc: Int,
    val awgJmin: Int,
    val awgJmax: Int,
    val awgS1: Int,
    val awgS2: Int,
    val awgH1: Long,
    val awgH2: Long,
    val awgH3: Long,
    val awgH4: Long,
)

data class BackupSplitRule(
    val packageName: String,
    val isExcluded: Boolean,
)
