package ru.ozero.enginebyedpi.strategy

import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

class ByeDpiKnownSeedsTest {

    @Test
    fun `commands list is non-empty`() {
        assertTrue(ByeDpiKnownSeeds.commands.isNotEmpty())
    }

    @Test
    fun `all commands start with dash`() {
        assertTrue(ByeDpiKnownSeeds.commands.all { it.startsWith("-") })
    }

    @Test
    fun `commands are distinct`() {
        val commands = ByeDpiKnownSeeds.commands
        assertTrue(commands.size == commands.distinct().size)
    }

    @Test
    fun `commands contain baseline minus-K`() {
        assertTrue(ByeDpiKnownSeeds.commands.contains("-K"))
    }

    @Test
    fun `GenePool accepts known seeds without error`() {
        val pool = GenePool(ByeDpiKnownSeeds.commands)
        val gene = pool.randomGene()
        assertTrue(gene.token.isNotEmpty())
    }
}
