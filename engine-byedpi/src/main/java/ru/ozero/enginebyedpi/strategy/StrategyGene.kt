package ru.ozero.enginebyedpi.strategy

data class StrategyGene(val token: String) {
    override fun toString(): String = token
}

typealias Chromosome = List<StrategyGene>

fun Chromosome.toCommand(): String = joinToString(" ") { it.token }

fun parseChromosome(command: String): Chromosome =
    ByeDpiOptionBlocks.tokenize(command).map(::StrategyGene)

internal object ByeDpiOptionBlocks {
    private val detachedValueOptions = setOf("-n", "--fake", "--ttl", "--split", "--disorder")
    private val shortDetachedValueOptions = setOf(
        "-a",
        "-d",
        "-e",
        "-f",
        "-m",
        "-o",
        "-p",
        "-q",
        "-r",
        "-s",
        "-t",
        "-O",
        "-R",
    )

    fun tokenize(command: String): List<String> =
        command.trim().split(Regex("\\s+")).filter(String::isNotBlank)

    fun blocks(chromosome: Chromosome): List<Chromosome> {
        if (chromosome.isEmpty()) return emptyList()
        val blocks = mutableListOf<Chromosome>()
        var index = 0
        while (index < chromosome.size) {
            val token = chromosome[index].token
            val next = chromosome.getOrNull(index + 1)
            if (requiresDetachedValue(token) && next != null && acceptsDetachedValue(token, next.token)) {
                blocks += listOf(chromosome[index], next)
                index += 2
            } else {
                blocks += listOf(chromosome[index])
                index += 1
            }
        }
        return blocks
    }

    fun commandBlocks(command: String): List<Chromosome> = blocks(parseChromosome(command))

    fun flatten(blocks: List<Chromosome>): Chromosome = blocks.flatten()

    fun requiresDetachedValue(token: String): Boolean =
        token in detachedValueOptions || token in shortDetachedValueOptions

    fun acceptsDetachedValue(option: String, token: String): Boolean =
        when (option) {
            "-n" -> token.isNotBlank() && !token.startsWith("-")
            "--fake" -> token.toIntOrNull() != null
            "--ttl" -> token.toIntOrNull()?.let { it >= 0 } == true
            "--split", "--disorder" -> token.isNotBlank() && !token.startsWith("-")
            in shortDetachedValueOptions -> token.isNotBlank() && !token.startsWith("-")
            else -> false
        }
}
