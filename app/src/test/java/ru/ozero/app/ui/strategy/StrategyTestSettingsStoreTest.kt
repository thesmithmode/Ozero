package ru.ozero.app.ui.strategy

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StrategyTestSettingsStoreTest {

    private lateinit var store: InMemoryStrategyTestSettingsStore

    @BeforeEach
    fun setUp() {
        store = InMemoryStrategyTestSettingsStore()
    }

    @Test
    fun `load returns defaults when no prefs saved`() {
        val s = store.load()
        assertEquals(1, s.requestsPerDomain)
        assertEquals(20, s.concurrentLimit)
        assertEquals(5, s.timeoutSeconds)
        assertEquals(500L, s.delayBetweenMs)
        assertFalse(s.useCustomStrategies)
        assertEquals("", s.customStrategies)
        assertTrue(s.evolutionMode, "evolutionMode default обязан быть true — sentinel против отката")
        assertEquals(20, s.evolutionPopulationSize)
        assertEquals(10, s.evolutionMaxGenerations)
        assertEquals(0.2f, s.evolutionMutationRate)
        assertEquals(5, s.evolutionEliteCount)
    }

    @Test
    fun `save and load round-trips all fields including evolution`() {
        val settings = StrategyTestSettings(
            requestsPerDomain = 3,
            concurrentLimit = 10,
            timeoutSeconds = 15,
            delayBetweenMs = 1000L,
            useCustomStrategies = true,
            customStrategies = "--cmd1\n--cmd2",
            evolutionMode = true,
            evolutionPopulationSize = 50,
            evolutionMaxGenerations = 25,
            evolutionMutationRate = 0.4f,
            evolutionEliteCount = 8,
        )
        store.save(settings)
        val loaded = store.load()
        assertEquals(settings.requestsPerDomain, loaded.requestsPerDomain)
        assertEquals(settings.concurrentLimit, loaded.concurrentLimit)
        assertEquals(settings.timeoutSeconds, loaded.timeoutSeconds)
        assertEquals(settings.delayBetweenMs, loaded.delayBetweenMs)
        assertTrue(loaded.useCustomStrategies)
        assertEquals(settings.customStrategies, loaded.customStrategies)
        assertTrue(loaded.evolutionMode)
        assertEquals(settings.evolutionPopulationSize, loaded.evolutionPopulationSize)
        assertEquals(settings.evolutionMaxGenerations, loaded.evolutionMaxGenerations)
        assertEquals(settings.evolutionMutationRate, loaded.evolutionMutationRate)
        assertEquals(settings.evolutionEliteCount, loaded.evolutionEliteCount)
    }

    @Test
    fun `partial save preserves other fields`() {
        store.save(StrategyTestSettings(evolutionMode = true, evolutionPopulationSize = 30))
        val first = store.load()
        assertTrue(first.evolutionMode)
        assertEquals(30, first.evolutionPopulationSize)
        assertEquals(1, first.requestsPerDomain)
        assertEquals(20, first.concurrentLimit)
    }

    @Test
    fun `save overwrites previous settings`() {
        store.save(StrategyTestSettings(evolutionEliteCount = 3))
        store.save(StrategyTestSettings(evolutionEliteCount = 9))
        assertEquals(9, store.load().evolutionEliteCount)
    }

    @Test
    fun `evolution mutation rate boundary values round-trip`() {
        store.save(StrategyTestSettings(evolutionMutationRate = 0.0f))
        assertEquals(0.0f, store.load().evolutionMutationRate)
        store.save(StrategyTestSettings(evolutionMutationRate = 1.0f))
        assertEquals(1.0f, store.load().evolutionMutationRate)
    }

    @Test
    fun `multiple save-load cycles remain consistent`() {
        repeat(5) { i ->
            store.save(StrategyTestSettings(evolutionMaxGenerations = i * 10 + 1))
        }
        assertEquals(41, store.load().evolutionMaxGenerations)
    }
}

private class InMemoryStrategyTestSettingsStore : StrategyTestSettingsStore {
    private var current = StrategyTestSettings()

    override fun load(): StrategyTestSettings = current

    override fun save(settings: StrategyTestSettings) {
        current = settings
    }
}
