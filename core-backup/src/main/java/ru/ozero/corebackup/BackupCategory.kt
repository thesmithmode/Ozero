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

    companion object {
        val ALL: Set<BackupCategory> = values().toSet()
    }
}
