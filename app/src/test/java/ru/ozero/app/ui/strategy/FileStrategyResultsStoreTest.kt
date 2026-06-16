package ru.ozero.app.ui.strategy

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FileStrategyResultsStoreTest {

    private lateinit var tempDir: File
    private lateinit var store: FileStrategyResultsStore

    @BeforeEach
    fun setUp() {
        tempDir = Files.createTempDirectory("strategy_results_store_test").toFile()
        store = FileStrategyResultsStore(tempDir)
    }

    @AfterEach
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `load returns empty when results file does not exist`() {
        assertEquals(emptyList(), store.load())
    }

    @Test
    fun `load returns empty for blank file`() {
        File(tempDir, "proxy_test_results.json").writeText("  \n  ")

        assertEquals(emptyList(), store.load())
    }

    @Test
    fun `load returns empty for corrupted json`() {
        File(tempDir, "proxy_test_results.json").writeText("not json")

        assertEquals(emptyList(), store.load())
    }

    @Test
    fun `save and load preserves all result fields`() {
        store.save(
            listOf(
                StrategyResult(
                    command = "-Ku -An",
                    successCount = 7,
                    totalRequests = 10,
                    currentProgress = 9,
                    isCompleted = true,
                    avgDurationMs = 123,
                    lastSite = "example.org",
                    lastError = "timeout",
                ),
            ),
        )

        val result = store.load().single()
        assertEquals("-Ku -An", result.command)
        assertEquals(7, result.successCount)
        assertEquals(10, result.totalRequests)
        assertEquals(9, result.currentProgress)
        assertEquals(true, result.isCompleted)
        assertEquals(123, result.avgDurationMs)
        assertEquals("example.org", result.lastSite)
        assertEquals("timeout", result.lastError)
    }

    @Test
    fun `load converts blank optional strings to null`() {
        File(tempDir, "proxy_test_results.json").writeText(
            """
            [
              {
                "command": "-Ku",
                "successCount": 1,
                "totalRequests": 2,
                "currentProgress": 2,
                "isCompleted": true,
                "avgDurationMs": 10,
                "lastSite": "",
                "lastError": ""
              }
            ]
            """.trimIndent(),
        )

        val result = store.load().single()
        assertNull(result.lastSite)
        assertNull(result.lastError)
    }

    @Test
    fun `save creates custom file name`() {
        val custom = FileStrategyResultsStore(tempDir, "custom-results.json")

        custom.save(listOf(StrategyResult(command = "-custom")))

        assertEquals("-custom", custom.load().single().command)
        assertEquals(false, File(tempDir, "proxy_test_results.json").exists())
    }

    @Test
    fun `successPercentage is zero when no requests were made`() {
        assertEquals(0, StrategyResult(command = "-Ku").successPercentage)
    }

    @Test
    fun `successPercentage uses integer percent of successful requests`() {
        assertEquals(66, StrategyResult(command = "-Ku", successCount = 2, totalRequests = 3).successPercentage)
    }
}
