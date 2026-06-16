package ru.ozero.enginebyedpi.strategy

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ByeDpiOptionBlocksTest {

    @Test
    fun `accepts detached values validates each supported option family`() {
        assertTrue(ByeDpiOptionBlocks.acceptsDetachedValue("-n", "front.example"))
        assertTrue(ByeDpiOptionBlocks.acceptsDetachedValue("--fake", "-1"))
        assertTrue(ByeDpiOptionBlocks.acceptsDetachedValue("--ttl", "0"))
        assertTrue(ByeDpiOptionBlocks.acceptsDetachedValue("--split", "1+s"))
        assertTrue(ByeDpiOptionBlocks.acceptsDetachedValue("--disorder", "2+d"))
        listOf("-a", "-d", "-e", "-f", "-m", "-o", "-p", "-q", "-r", "-s", "-t", "-O", "-R")
            .forEach { option ->
                assertTrue(ByeDpiOptionBlocks.acceptsDetachedValue(option, "4+h"))
            }

        assertFalse(ByeDpiOptionBlocks.acceptsDetachedValue("-n", "-front.example"))
        assertFalse(ByeDpiOptionBlocks.acceptsDetachedValue("-n", ""))
        assertFalse(ByeDpiOptionBlocks.acceptsDetachedValue("--fake", "abc"))
        assertFalse(ByeDpiOptionBlocks.acceptsDetachedValue("--ttl", "-1"))
        assertFalse(ByeDpiOptionBlocks.acceptsDetachedValue("--ttl", "abc"))
        assertFalse(ByeDpiOptionBlocks.acceptsDetachedValue("--split", "-1+s"))
        assertFalse(ByeDpiOptionBlocks.acceptsDetachedValue("--split", ""))
        assertFalse(ByeDpiOptionBlocks.acceptsDetachedValue("--disorder", "-2+d"))
        assertFalse(ByeDpiOptionBlocks.acceptsDetachedValue("--disorder", ""))
        assertFalse(ByeDpiOptionBlocks.acceptsDetachedValue("-a", "-3"))
        assertFalse(ByeDpiOptionBlocks.acceptsDetachedValue("-R", ""))
        assertFalse(ByeDpiOptionBlocks.acceptsDetachedValue("--unknown", "1"))
    }

    @Test
    fun `blocks keep detached values with their owner and leave invalid values standalone`() {
        val blocks = ByeDpiOptionBlocks.commandBlocks("-n front.example --fake -1 --ttl -1 -a 2 -R -bad -K")

        assertEquals(
            listOf(
                listOf("-n", "front.example"),
                listOf("--fake", "-1"),
                listOf("--ttl"),
                listOf("-1"),
                listOf("-a", "2"),
                listOf("-R"),
                listOf("-bad"),
                listOf("-K"),
            ),
            blocks.map { block -> block.map { it.token } },
        )
    }
}
