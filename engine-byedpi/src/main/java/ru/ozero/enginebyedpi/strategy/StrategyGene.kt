package ru.ozero.enginebyedpi.strategy

data class StrategyGene(val token: String) {
    override fun toString(): String = token
}

typealias Chromosome = List<StrategyGene>

fun Chromosome.toCommand(): String = joinToString(" ") { it.token }

fun parseChromosome(command: String): Chromosome =
    command.split(" ").filter(String::isNotBlank).map(::StrategyGene)
