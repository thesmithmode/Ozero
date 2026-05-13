package ru.ozero.app.ui.strategy

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UsageHistoryStoreTest {

    private lateinit var tempDir: File
    private lateinit var store: FileUsageHistoryStore

    @BeforeEach
    fun setUp() {
        tempDir = Files.createTempDirectory("usage_history_test").toFile()
        store = FileUsageHistoryStore(tempDir)
    }

    @AfterEach
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `load returns empty when file does not exist`() {
        assertEquals(emptyList(), store.load())
    }

    @Test
    fun `record saves entry and load returns it`() {
        store.record("-Ku -An", name = "Для ютуба")
        val entries = store.load()
        assertEquals(1, entries.size)
        assertEquals("-Ku -An", entries[0].command)
        assertEquals("Для ютуба", entries[0].name)
        assertTrue(entries[0].appliedAt > 0)
    }

    @Test
    fun `record prepends newest entry first`() {
        store.record("-cmd1", name = null)
        store.record("-cmd2", name = null)
        val entries = store.load()
        assertEquals("-cmd2", entries[0].command)
        assertEquals("-cmd1", entries[1].command)
    }

    @Test
    fun `record trims to maxEntries`() {
        val small = FileUsageHistoryStore(tempDir, "trim_test.json", maxEntries = 3)
        repeat(5) { i -> small.record("-cmd$i", null) }
        assertEquals(3, small.load().size)
    }

    @Test
    fun `record null name stores as null`() {
        store.record("-Ku", name = null)
        assertEquals(null, store.load()[0].name)
    }

    @Test
    fun `load survives corrupted file`() {
        File(tempDir, "strategy_usage_history.json").writeText("not valid json")
        assertEquals(emptyList(), store.load())
    }
}
