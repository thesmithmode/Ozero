package ru.ozero.corebackup

enum class BackupCategory {
    GENERAL_SETTINGS,
    DNS_HOSTS,
    BYEDPI,
    WARP,
    URNETWORK,
    STRATEGY,
    SPLIT_TUNNEL,
    ;

    fun isPresentIn(data: AppBackupData): Boolean = when (this) {
        GENERAL_SETTINGS -> data.settings.hasGeneral()
        DNS_HOSTS -> data.settings.hasDns()
        BYEDPI -> data.settings.hasByedpi()
        WARP -> data.warpSlots.isNotEmpty()
        URNETWORK -> data.settings.hasUrnPrefs() || data.urnetwork.hasAny()
        STRATEGY -> data.strategy != null
        SPLIT_TUNNEL -> data.splitRules.isNotEmpty()
    }

    companion object {
        val ALL: Set<BackupCategory> = values().toSet()

        fun availableIn(data: AppBackupData): Set<BackupCategory> =
            values().filter { it.isPresentIn(data) }.toSet()
    }
}

private fun BackupSettings.hasGeneral(): Boolean = listOfNotNull(
    splitMode, ipv6Enabled, autoStart, manualEngine, engineAutoPriority, uiLocaleTag, appMode,
).isNotEmpty()

private fun BackupSettings.hasDns(): Boolean =
    listOfNotNull(customDnsServers, hostsMode, hostsList).isNotEmpty()

private fun BackupSettings.hasByedpi(): Boolean = listOfNotNull(
    bydpiWinningArgs, bydpiUseUiMode, bydpiUiSettingsJson, bydpiDefaultAccepted,
).isNotEmpty()

private fun BackupSettings.hasUrnPrefs(): Boolean =
    listOfNotNull(urnetworkEnabled, urnetworkJwt, urnetworkCountryCode).isNotEmpty()

private fun BackupUrnetwork.hasAny(): Boolean = listOfNotNull(
    byJwt, windowType, fixedIpSize, allowDirect, provideEnabled,
    provideControlMode, provideNetworkMode, selectedLocation,
).isNotEmpty()
