package ru.ozero.enginebyedpi.strategy

import java.io.File

data class EvolutionResources(
    val memory: GeneMemory,
    val fitnessCache: StrategyFitnessCache,
    val networkId: String,
)

interface EvolutionResourcesProvider {
    fun forNetwork(networkId: String): EvolutionResources
}

class DefaultEvolutionResourcesProvider(
    private val filesDir: File,
) : EvolutionResourcesProvider {

    private val memoryCache = mutableMapOf<String, GeneMemory>()
    private val fitnessByNetwork = mutableMapOf<String, StrategyFitnessCache>()

    @Synchronized
    override fun forNetwork(networkId: String): EvolutionResources {
        val safeId = sanitize(networkId)
        val memory = memoryCache.getOrPut(safeId) {
            GeneMemory(File(filesDir, "evolution_memory_$safeId.json")).also { it.load() }
        }
        val cache = fitnessByNetwork.getOrPut(safeId) {
            StrategyFitnessCache(File(filesDir, "fitness_cache_$safeId.json")).also { it.load() }
        }
        return EvolutionResources(memory = memory, fitnessCache = cache, networkId = safeId)
    }

    private fun sanitize(id: String): String =
        id.ifBlank { "unknown" }.filter { it.isLetterOrDigit() || it == '_' || it == '-' }.take(MAX_ID_LEN)

    companion object {
        private const val MAX_ID_LEN: Int = 32
    }
}
