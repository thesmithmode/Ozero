package ru.ozero.corebackup

data class AppBackupData(
    val version: Int = CURRENT_VERSION,
    val exportedAt: String,
    val settings: BackupSettings,
    val urnetwork: BackupUrnetwork,
    val warpSlots: List<BackupWarpSlot>,
    val splitRules: List<BackupSplitRule>,
    val strategy: BackupStrategy? = null,
    val telegram: BackupTelegram? = null,
) {
    companion object {
        const val CURRENT_VERSION = 3
    }
}

data class BackupTelegram(
    val enabled: Boolean? = null,
    val port: Int? = null,
    val domain: String? = null,
    val secret: String? = null,
)

data class BackupStrategy(
    val settings: BackupStrategySettings? = null,
    val domainLists: List<BackupDomainList> = emptyList(),
    val savedStrategies: List<BackupSavedStrategy> = emptyList(),
)

data class BackupStrategySettings(
    val requestsPerDomain: Int? = null,
    val concurrentLimit: Int? = null,
    val timeoutSeconds: Int? = null,
    val delayBetweenMs: Long? = null,
    val useCustomStrategies: Boolean? = null,
    val customStrategies: String? = null,
    val evolutionMode: Boolean? = null,
    val evolutionPopulationSize: Int? = null,
    val evolutionMaxGenerations: Int? = null,
    val evolutionMutationRate: Float? = null,
    val evolutionEliteCount: Int? = null,
    val evolutionTargetFitness: Float? = null,
)

data class BackupDomainList(
    val id: String,
    val name: String,
    val domains: List<String>,
    val isActive: Boolean,
    val isBuiltIn: Boolean,
)

data class BackupSavedStrategy(
    val id: String,
    val command: String,
    val name: String? = null,
    val isPinned: Boolean = false,
)

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
    val engineAutoPriority: String? = null,
    val bydpiUseUiMode: Boolean? = null,
    val bydpiUiSettingsJson: String? = null,
    val bydpiDefaultAccepted: Boolean? = null,
    val urnetworkCountryCode: String? = null,
)

data class BackupUrnetwork(
    val byJwt: String? = null,
    val windowType: String? = null,
    val fixedIpSize: Boolean? = null,
    val allowDirect: Boolean? = null,
    val provideEnabled: Boolean? = null,
    val provideControlMode: String? = null,
    val provideNetworkMode: String? = null,
    val selectedLocation: BackupUrnetworkLocation? = null,
)

data class BackupUrnetworkLocation(
    val countryCode: String? = null,
    val region: String? = null,
    val city: String? = null,
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
    val awgS3: Int? = null,
    val awgS4: Int? = null,
    val awgI1: Int? = null,
    val awgI2: Int? = null,
    val awgI3: Int? = null,
    val awgI4: Int? = null,
    val awgI5: Int? = null,
    val awgI1Hex: String? = null,
    val awgI2Hex: String? = null,
    val awgI3Hex: String? = null,
    val awgI4Hex: String? = null,
    val awgI5Hex: String? = null,
)

data class BackupSplitRule(
    val packageName: String,
    val isExcluded: Boolean,
)
