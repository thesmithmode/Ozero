package ru.ozero.app.ui.strategy

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SavedStrategyStoreTest {

    private lateinit var tempDir: File
    private lateinit var store: FileSavedStrategyStore

    @BeforeEach
    fun setUp() {
        tempDir = Files.createTempDirectory("saved_strategy_test").toFile()
        store = FileSavedStrategyStore(tempDir)
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
    fun `save and load round-trips all fields`() {
        val strategy = SavedStrategy(
            id = "abc",
            command = "-Ku -An",
            name = "My Strategy",
            isPinned = true,
            addedAt = 1000L,
        )
        store.save(listOf(strategy))
        val loaded = store.load()
        assertEquals(1, loaded.size)
        assertEquals("abc", loaded[0].id)
        assertEquals("-Ku -An", loaded[0].command)
        assertEquals("My Strategy", loaded[0].name)
        assertTrue(loaded[0].isPinned)
        assertEquals(1000L, loaded[0].addedAt)
    }

    @Test
    fun `save with null name round-trips as null`() {
        store.save(listOf(SavedStrategy(id = "x", command = "-cmd", name = null, addedAt = 1L)))
        val loaded = store.load()
        assertNull(loaded[0].name)
    }

    @Test
    fun `load returns empty on corrupted file`() {
        File(tempDir, "saved_strategies.json").writeText("{bad}")
        assertEquals(emptyList(), store.load())
    }

    @Test
    fun `add extension deduplicates same command`() {
        store.add("-cmd1")
        store.add("-cmd1")
        assertEquals(1, store.load().size)
    }

    @Test
    fun `add extension returns updated list`() {
        val result = store.add("-cmd1")
        assertEquals(1, result.size)
        assertEquals("-cmd1", result[0].command)
        assertFalse(result[0].isPinned)
        assertNotNull(result[0].id)
    }

    @Test
    fun `pin extension sets isPinned true`() {
        store.add("-cmd")
        val id = store.load()[0].id
        store.pin(id)
        assertTrue(store.load()[0].isPinned)
    }

    @Test
    fun `unpin extension clears isPinned`() {
        val pinned = SavedStrategy(id = "p", command = "-p", isPinned = true, addedAt = 1L)
        store.save(listOf(pinned))
        store.unpin("p")
        assertFalse(store.load()[0].isPinned)
    }

    @Test
    fun `rename extension updates name`() {
        store.save(listOf(SavedStrategy(id = "r", command = "-r", addedAt = 1L)))
        store.rename("r", "My Name")
        assertEquals("My Name", store.load()[0].name)
    }

    @Test
    fun `delete extension removes entry by id`() {
        store.save(
            listOf(
                SavedStrategy(id = "keep", command = "-k", addedAt = 1L),
                SavedStrategy(id = "del", command = "-d", addedAt = 2L),
            ),
        )
        store.delete("del")
        val loaded = store.load()
        assertEquals(1, loaded.size)
        assertEquals("keep", loaded[0].id)
    }

    @Test
    fun `overflow trims oldest unpinned keeping pinned`() {
        val small = FileSavedStrategyStore(tempDir, "small.json", maxUnpinned = 3)
        val pinned = SavedStrategy(id = "pinned", command = "-pin", isPinned = true, addedAt = 1L)
        val unpinned = (1..5).map { i ->
            SavedStrategy(id = "u$i", command = "-u$i", isPinned = false, addedAt = i.toLong())
        }
        small.save(listOf(pinned) + unpinned)
        val loaded = small.load()
        assertTrue(loaded.any { it.id == "pinned" }, "pinned entry preserved")
        val unpinnedLoaded = loaded.filter { !it.isPinned }
        assertEquals(3, unpinnedLoaded.size, "unpinned trimmed to 3")
        assertTrue(unpinnedLoaded.none { it.id == "u1" }, "oldest unpinned dropped")
        assertTrue(unpinnedLoaded.none { it.id == "u2" }, "second oldest dropped")
    }

    @Test
    fun `lastVerifiedAtMs round-trips`() {
        store.save(listOf(SavedStrategy(id = "v", command = "-v", addedAt = 1L, lastVerifiedAtMs = 42L)))
        assertEquals(42L, store.load()[0].lastVerifiedAtMs)
    }

    @Test
    fun `lastVerifiedAtMs defaults to 0 when missing in stored file`() {
        File(tempDir, "saved_strategies.json").writeText(
            """[{"id":"x","command":"-x","name":"","isPinned":false,"addedAt":1}]""",
        )
        assertEquals(0L, store.load()[0].lastVerifiedAtMs)
    }

    @Test
    fun `markVerified updates lastVerifiedAtMs for matching commands only`() {
        store.save(
            listOf(
                SavedStrategy(id = "a", command = "-cmdA", addedAt = 1L),
                SavedStrategy(id = "b", command = "-cmdB", addedAt = 2L),
            ),
        )
        store.markVerified(setOf("-cmdA"), 999L)
        val loaded = store.load().associateBy { it.id }
        assertEquals(999L, loaded["a"]!!.lastVerifiedAtMs)
        assertEquals(0L, loaded["b"]!!.lastVerifiedAtMs)
    }

    @Test
    fun `markVerified with empty set is no-op`() {
        store.save(listOf(SavedStrategy(id = "a", command = "-c", addedAt = 1L, lastVerifiedAtMs = 50L)))
        store.markVerified(emptySet(), 999L)
        assertEquals(50L, store.load()[0].lastVerifiedAtMs)
    }

    @Test
    fun `concurrent add preserves all distinct commands`() {
        val big = FileSavedStrategyStore(tempDir, "race.json", maxUnpinned = 1_000)
        val threads = (1..8).map { idx ->
            Thread { repeat(20) { i -> big.add("--cmd-$idx-$i") } }.also { it.start() }
        }
        threads.forEach { it.join() }
        val distinct = big.load().map { it.command }.toSet()
        assertEquals(8 * 20, distinct.size, "no commands lost to race: ${distinct.size}")
    }

    @Test
    fun `bestNetworks round-trips`() {
        store.save(
            listOf(
                SavedStrategy(
                    id = "n",
                    command = "-n",
                    addedAt = 1L,
                    bestNetworks = setOf("net-a", "net-b"),
                ),
            ),
        )
        val loaded = store.load()
        assertEquals(setOf("net-a", "net-b"), loaded[0].bestNetworks)
    }

    @Test
    fun `bestNetworks defaults to empty when missing in stored file`() {
        File(tempDir, "saved_strategies.json").writeText(
            """[{"id":"x","command":"-x","name":"","isPinned":false,"addedAt":1}]""",
        )
        assertEquals(emptySet<String>(), store.load()[0].bestNetworks)
    }

    @Test
    fun `markBestOnNetwork adds networkId only to matching commands`() {
        store.save(
            listOf(
                SavedStrategy(id = "a", command = "-cmdA", addedAt = 1L),
                SavedStrategy(id = "b", command = "-cmdB", addedAt = 2L),
            ),
        )
        store.markBestOnNetwork(setOf("-cmdA"), "net-wifi")
        val loaded = store.load().associateBy { it.id }
        assertEquals(setOf("net-wifi"), loaded["a"]!!.bestNetworks)
        assertEquals(emptySet<String>(), loaded["b"]!!.bestNetworks)
    }

    @Test
    fun `markBestOnNetwork accumulates multiple networks`() {
        store.save(listOf(SavedStrategy(id = "a", command = "-a", addedAt = 1L)))
        store.markBestOnNetwork(setOf("-a"), "net-1")
        store.markBestOnNetwork(setOf("-a"), "net-2")
        assertEquals(setOf("net-1", "net-2"), store.load()[0].bestNetworks)
    }

    @Test
    fun `markBestOnNetwork with empty commands is no-op`() {
        store.save(
            listOf(SavedStrategy(id = "a", command = "-a", addedAt = 1L, bestNetworks = setOf("net-x"))),
        )
        store.markBestOnNetwork(emptySet(), "net-y")
        assertEquals(setOf("net-x"), store.load()[0].bestNetworks)
    }
}
