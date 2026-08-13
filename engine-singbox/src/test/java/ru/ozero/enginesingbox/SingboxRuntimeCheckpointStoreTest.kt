package ru.ozero.enginesingbox

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SingboxRuntimeCheckpointStoreTest {
    @TempDir
    lateinit var directory: File

    @Test
    fun `returns last 64 sanitized checkpoints for requested process`() {
        repeat(70) { index ->
            SingboxRuntimeCheckpointStore.record(
                directory = directory,
                message = "checkpoint-$index token=abcdefghijklmnopqrstuvwxyz1234567890",
                timestamp = index.toLong(),
                pid = 42,
            )
        }
        SingboxRuntimeCheckpointStore.record(directory, "other-process", timestamp = 1L, pid = 7)

        val checkpoints = SingboxRuntimeCheckpointStore.read(directory, 42)

        assertEquals(64, checkpoints.size)
        assertTrue(checkpoints.first().contains("checkpoint-6"))
        assertTrue(checkpoints.last().contains("checkpoint-69"))
        assertTrue(checkpoints.all { "<redacted-token>" in it })
        assertFalse(checkpoints.any { "other-process" in it })
    }
}
