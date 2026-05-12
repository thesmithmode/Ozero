package ru.ozero.app.ui.strategy

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DomainListStoreTest {

    private lateinit var tempDir: File
    private lateinit var store: FileDomainListStore

    @BeforeEach
    fun setUp() {
        tempDir = Files.createTempDirectory("domain_list_test").toFile()
        store = FileDomainListStore(tempDir)
    }

    @AfterEach
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `load returns empty list when file does not exist`() {
        assertEquals(emptyList(), store.load())
    }

    @Test
    fun `save then load round-trips all fields`() {
        val lists = listOf(
            DomainList(id = "id1", name = "General", domains = listOf("a.com", "b.com"), isActive = true, isBuiltIn = true),
            DomainList(id = "id2", name = "Custom", domains = listOf("c.com"), isActive = false, isBuiltIn = false),
        )
        store.save(lists)
        val loaded = store.load()
        assertEquals(2, loaded.size)
        assertEquals("id1", loaded[0].id)
        assertEquals("General", loaded[0].name)
        assertEquals(listOf("a.com", "b.com"), loaded[0].domains)
        assertTrue(loaded[0].isActive)
        assertTrue(loaded[0].isBuiltIn)
        assertEquals("id2", loaded[1].id)
        assertEquals("Custom", loaded[1].name)
        assertEquals(listOf("c.com"), loaded[1].domains)
        assertTrue(!loaded[1].isActive)
        assertTrue(!loaded[1].isBuiltIn)
    }

    @Test
    fun `save then load with empty domains list`() {
        val lists = listOf(DomainList(id = "x", name = "Empty", domains = emptyList()))
        store.save(lists)
        val loaded = store.load()
        assertEquals(1, loaded.size)
        assertEquals(emptyList(), loaded[0].domains)
    }

    @Test
    fun `load returns empty on corrupted file`() {
        File(tempDir, "domain_lists.json").writeText("not-json")
        assertEquals(emptyList(), store.load())
    }

    @Test
    fun `save overwrites previous content`() {
        store.save(listOf(DomainList(id = "old", name = "Old", domains = listOf("old.com"))))
        store.save(listOf(DomainList(id = "new", name = "New", domains = listOf("new.com"))))
        val loaded = store.load()
        assertEquals(1, loaded.size)
        assertEquals("new", loaded[0].id)
    }

    @Test
    fun `load handles large domain list`() {
        val domains = (1..200).map { "site$it.com" }
        store.save(listOf(DomainList(id = "big", name = "Big", domains = domains)))
        val loaded = store.load()
        assertEquals(200, loaded[0].domains.size)
        assertEquals("site1.com", loaded[0].domains.first())
        assertEquals("site200.com", loaded[0].domains.last())
    }
}
