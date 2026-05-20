package ru.ozero.corebackup

enum class BackupCategory {
    GENERAL_SETTINGS,
    DNS_HOSTS,
    BYEDPI,
    WARP,
    URNETWORK,
    TELEGRAM,
    STRATEGY,
    SPLIT_TUNNEL,
    ;

    fun isPresentIn(data: AppBackupData): Boolean = when (this) {
        GENERAL_SETTINGS -> data.settings.hasGeneral()
        DNS_HOSTS -> data.settings.hasDns()
        BYEDPI -> data.settings.hasByedpi()
        WARP -> data.warpSlots.isNotEmpty()
        URNETWORK -> data.settings.hasUrnPrefs() || data.urnetwork.hasAny()
        TELEGRAM -> data.telegram != null
        STRATEGY -> data.strategy != null
        SPLIT_TUNNEL -> data.splitRules.isNotEmpty()
    }

    companion object {
        val ALL: Set<BackupCategory> = values().toSet()
        fun availableIn(data: AppBackupData): Set<BackupCategory> = values().filter { it.isPresentIn(data) }.toSet()
    }
}

private fun BackupSettings.hasGeneral(): Boolean =
    splitMode != null || ipv6Enabled != null || autoStart != null || manualEngine != null ||
        engineAutoPriority != null || uiLocaleTag != null || appMode != null

private fun BackupSettings.hasDns(): Boolean =
    customDnsServers != null || hostsMode != null || hostsList != null

private fun BackupSettings.hasByedpi(): Boolean =
    bydpiWinningArgs != null || bydpiUseUiMode != null ||
        bydpiUiSettingsJson != null || bydpiDefaultAccepted != null

private fun BackupSettings.hasUrnPrefs(): Boolean =
    urnetworkEnabled != null || urnetworkJwt != null || urnetworkCountryCode != null

private fun BackupUrnetwork.hasAny(): Boolean =
    byJwt != null || windowType != null || fixedIpSize != null || allowDirect != null ||
        provideEnabled != null || provideControlMode != null || provideNetworkMode != null ||
        selectedLocation != null
