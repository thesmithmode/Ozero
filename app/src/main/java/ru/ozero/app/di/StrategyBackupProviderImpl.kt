package ru.ozero.app.di

import ru.ozero.app.ui.strategy.DomainList
import ru.ozero.app.ui.strategy.DomainListStore
import ru.ozero.app.ui.strategy.SavedStrategy
import ru.ozero.app.ui.strategy.SavedStrategyStore
import ru.ozero.app.ui.strategy.StrategyTestSettings
import ru.ozero.app.ui.strategy.StrategyTestSettingsStore
import ru.ozero.corebackup.BackupDomainList
import ru.ozero.corebackup.BackupSavedStrategy
import ru.ozero.corebackup.BackupStrategy
import ru.ozero.corebackup.BackupStrategySettings
import ru.ozero.corebackup.StrategyBackupProvider

class StrategyBackupProviderImpl(
    private val settingsStore: StrategyTestSettingsStore,
    private val domainListStore: DomainListStore,
    private val savedStrategyStore: SavedStrategyStore,
) : StrategyBackupProvider {

    override suspend fun export(): BackupStrategy {
        val s = runCatching { settingsStore.load() }.getOrDefault(StrategyTestSettings())
        val domainLists = runCatching { domainListStore.load() }.getOrDefault(emptyList())
        val savedStrategies = runCatching { savedStrategyStore.load() }.getOrDefault(emptyList())
        return BackupStrategy(
            settings = BackupStrategySettings(
                requestsPerDomain = s.requestsPerDomain,
                concurrentLimit = s.concurrentLimit,
                timeoutSeconds = s.timeoutSeconds,
                delayBetweenMs = s.delayBetweenMs,
                useCustomStrategies = s.useCustomStrategies,
                customStrategies = s.customStrategies.ifBlank { null },
                evolutionMode = s.evolutionMode,
                evolutionPopulationSize = s.evolutionPopulationSize,
                evolutionMaxGenerations = s.evolutionMaxGenerations,
                evolutionMutationRate = s.evolutionMutationRate,
                evolutionEliteCount = s.evolutionEliteCount,
            ),
            domainLists = domainLists.map { it.toBackup() },
            savedStrategies = savedStrategies.map { it.toBackup() },
            evolutionMemory = null,
        )
    }

    override suspend fun import(strategy: BackupStrategy) {
        strategy.settings?.let { ss ->
            val current = runCatching { settingsStore.load() }.getOrDefault(StrategyTestSettings())
            settingsStore.save(
                current.copy(
                    requestsPerDomain = ss.requestsPerDomain ?: current.requestsPerDomain,
                    concurrentLimit = ss.concurrentLimit ?: current.concurrentLimit,
                    timeoutSeconds = ss.timeoutSeconds ?: current.timeoutSeconds,
                    delayBetweenMs = ss.delayBetweenMs ?: current.delayBetweenMs,
                    useCustomStrategies = ss.useCustomStrategies ?: current.useCustomStrategies,
                    customStrategies = ss.customStrategies ?: current.customStrategies,
                    evolutionMode = ss.evolutionMode ?: current.evolutionMode,
                    evolutionPopulationSize = ss.evolutionPopulationSize ?: current.evolutionPopulationSize,
                    evolutionMaxGenerations = ss.evolutionMaxGenerations ?: current.evolutionMaxGenerations,
                    evolutionMutationRate = ss.evolutionMutationRate ?: current.evolutionMutationRate,
                    evolutionEliteCount = ss.evolutionEliteCount ?: current.evolutionEliteCount,
                ),
            )
        }
        if (strategy.domainLists.isNotEmpty()) {
            domainListStore.save(strategy.domainLists.map { it.toDomain() })
        }
        if (strategy.savedStrategies.isNotEmpty()) {
            savedStrategyStore.save(strategy.savedStrategies.map { it.toSaved() })
        }
    }

    private fun DomainList.toBackup() = BackupDomainList(
        id = id, name = name, domains = domains, isActive = isActive, isBuiltIn = isBuiltIn,
    )

    private fun SavedStrategy.toBackup() = BackupSavedStrategy(
        id = id, command = command, name = name, isPinned = isPinned,
    )

    private fun BackupDomainList.toDomain() = DomainList(
        id = id, name = name, domains = domains, isActive = isActive, isBuiltIn = isBuiltIn,
    )

    private fun BackupSavedStrategy.toSaved() = SavedStrategy(
        id = id, command = command, name = name, isPinned = isPinned,
    )
}
