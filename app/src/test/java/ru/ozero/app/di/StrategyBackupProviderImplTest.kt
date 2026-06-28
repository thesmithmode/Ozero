package ru.ozero.app.di

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import ru.ozero.app.ui.strategy.DomainList
import ru.ozero.app.ui.strategy.DomainListStore
import ru.ozero.app.ui.strategy.SavedStrategy
import ru.ozero.app.ui.strategy.SavedStrategyStore
import ru.ozero.app.ui.strategy.StrategyTestSettings
import ru.ozero.app.ui.strategy.StrategyTestSettingsLimits
import ru.ozero.app.ui.strategy.StrategyTestSettingsStore
import ru.ozero.corebackup.BackupStrategy
import ru.ozero.corebackup.BackupStrategySettings
import kotlin.test.assertEquals

class StrategyBackupProviderImplTest {

    @Test
    fun `import clamps attacker controlled strategy settings`() = runTest {
        val settingsStore = FakeStrategyTestSettingsStore()
        val provider = StrategyBackupProviderImpl(
            settingsStore = settingsStore,
            domainListStore = FakeDomainListStore(),
            savedStrategyStore = FakeSavedStrategyStore(),
        )

        provider.import(
            BackupStrategy(
                settings = BackupStrategySettings(
                    requestsPerDomain = Int.MAX_VALUE,
                    concurrentLimit = Int.MAX_VALUE,
                    timeoutSeconds = Int.MAX_VALUE,
                    delayBetweenMs = Long.MAX_VALUE,
                    evolutionPopulationSize = Int.MAX_VALUE,
                    evolutionMaxGenerations = Int.MAX_VALUE,
                    evolutionMutationRate = Float.POSITIVE_INFINITY,
                    evolutionEliteCount = Int.MAX_VALUE,
                    evolutionTargetFitness = Float.NaN,
                ),
            ),
        )

        assertEquals(20, settingsStore.saved.requestsPerDomain)
        assertEquals(50, settingsStore.saved.concurrentLimit)
        assertEquals(15, settingsStore.saved.timeoutSeconds)
        assertEquals(5_000L, settingsStore.saved.delayBetweenMs)
        assertEquals(
            StrategyTestSettingsLimits.MAX_EVOLUTION_POPULATION_SIZE,
            settingsStore.saved.evolutionPopulationSize,
        )
        assertEquals(100, settingsStore.saved.evolutionMaxGenerations)
        assertEquals(0.2f, settingsStore.saved.evolutionMutationRate)
        assertEquals(
            StrategyTestSettingsLimits.MAX_EVOLUTION_POPULATION_SIZE,
            settingsStore.saved.evolutionEliteCount,
        )
        assertEquals(0.85f, settingsStore.saved.evolutionTargetFitness)
    }
}

private class FakeStrategyTestSettingsStore : StrategyTestSettingsStore {
    var saved = StrategyTestSettings()

    override fun load(): StrategyTestSettings = saved

    override fun save(settings: StrategyTestSettings) {
        saved = settings
    }
}

private class FakeDomainListStore : DomainListStore {
    override fun load(): List<DomainList> = emptyList()

    override fun save(lists: List<DomainList>) = Unit
}

private class FakeSavedStrategyStore : SavedStrategyStore {
    override fun load(): List<SavedStrategy> = emptyList()

    override fun save(strategies: List<SavedStrategy>) = Unit
}
