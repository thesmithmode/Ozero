package ru.ozero.corebackup

interface StrategyBackupProvider {
    suspend fun export(): BackupStrategy
    suspend fun import(strategy: BackupStrategy)
}
