package ru.ozero.app.ui.strategy

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DomainListManagerTest {

    private lateinit var store: FakeStore
    private lateinit var manager: DomainListManager

    private val builtIn1 = DomainList(id = "general", name = "General", domains = listOf("a.com", "b.com"), isActive = true, isBuiltIn = true)
    private val builtIn2 = DomainList(id = "youtube", name = "YouTube", domains = listOf("yt.com"), isActive = true, isBuiltIn = true)
    private val builtIn3 = DomainList(id = "inactive", name = "Inactive", domains = listOf("c.com"), isActive = false, isBuiltIn = true)

    @BeforeEach
    fun setUp() {
        store = FakeStore()
        manager = DomainListManager(store, listOf(builtIn1, builtIn2, builtIn3))
    }

    @Test
    fun `load on empty store saves and returns built-ins`() {
        val result = manager.load()
        assertEquals(3, result.size)
        assertEquals("general", result[0].id)
        assertEquals(listOf("general", "youtube", "inactive"), result.map { it.id })
        assertEquals(listOf(builtIn1, builtIn2, builtIn3), store.saved)
    }

    @Test
    fun `load syncs built-ins preserving user active preference`() {
        store.data = listOf(
            builtIn1.copy(isActive = false),
            builtIn2,
        )
        val result = manager.load()
        assertEquals(3, result.size)
        assertFalse(result.first { it.id == "general" }.isActive, "user toggled off preserved")
        assertTrue(result.first { it.id == "youtube" }.isActive)
        assertTrue(result.first { it.id == "inactive" }.id == "inactive", "new built-in added")
    }

    @Test
    fun `load syncs updated domains from built-in`() {
        val outdated = builtIn1.copy(domains = listOf("old.com"))
        store.data = listOf(outdated, builtIn2, builtIn3)
        val result = manager.load()
        assertEquals(listOf("a.com", "b.com"), result.first { it.id == "general" }.domains)
    }

    @Test
    fun `load preserves custom lists`() {
        val custom = DomainList(id = "custom-id", name = "My List", domains = listOf("x.com"), isBuiltIn = false)
        store.data = listOf(builtIn1, builtIn2, builtIn3, custom)
        val result = manager.load()
        assertEquals(4, result.size)
        assertTrue(result.any { it.id == "custom-id" })
    }

    @Test
    fun `getActiveDomains returns flat distinct domains from active lists`() {
        val lists = listOf(
            DomainList(id = "a", name = "A", domains = listOf("x.com", "y.com"), isActive = true),
            DomainList(id = "b", name = "B", domains = listOf("y.com", "z.com"), isActive = true),
            DomainList(id = "c", name = "C", domains = listOf("w.com"), isActive = false),
        )
        val domains = manager.getActiveDomains(lists)
        assertEquals(3, domains.size)
        assertTrue(domains.containsAll(listOf("x.com", "y.com", "z.com")))
        assertFalse(domains.contains("w.com"))
    }

    @Test
    fun `getActiveDomains returns empty when all inactive`() {
        val lists = listOf(
            DomainList(id = "a", name = "A", domains = listOf("x.com"), isActive = false),
        )
        assertEquals(emptyList(), manager.getActiveDomains(lists))
    }

    @Test
    fun `toggle flips isActive for matching id`() {
        val lists = listOf(builtIn1, builtIn2)
        val updated = manager.toggle(lists, "general")
        assertFalse(updated.first { it.id == "general" }.isActive)
        assertTrue(updated.first { it.id == "youtube" }.isActive)
    }

    @Test
    fun `toggle does not affect unmatched ids`() {
        val lists = listOf(builtIn1)
        val updated = manager.toggle(lists, "nonexistent")
        assertEquals(lists, updated)
    }

    @Test
    fun `addCustom appends new non-builtin list`() {
        val lists = listOf(builtIn1)
        val updated = manager.addCustom(lists, "Mine", listOf("my.com"))
        assertEquals(2, updated.size)
        val custom = updated.last()
        assertEquals("Mine", custom.name)
        assertEquals(listOf("my.com"), custom.domains)
        assertFalse(custom.isBuiltIn)
        assertTrue(custom.id.isNotBlank())
    }

    @Test
    fun `addCustom generates unique ids`() {
        var lists = listOf(builtIn1)
        lists = manager.addCustom(lists, "A", listOf("a.com"))
        lists = manager.addCustom(lists, "B", listOf("b.com"))
        val ids = lists.map { it.id }
        assertEquals(ids.distinct(), ids)
    }

    @Test
    fun `delete removes list by id`() {
        val custom = DomainList(id = "del-me", name = "Del", domains = listOf("d.com"), isBuiltIn = false)
        val lists = listOf(builtIn1, custom)
        val updated = manager.delete(lists, "del-me")
        assertEquals(1, updated.size)
        assertFalse(updated.any { it.id == "del-me" })
    }

    @Test
    fun `delete built-in removes it from active list`() {
        val lists = listOf(builtIn1, builtIn2)
        val updated = manager.delete(lists, "general")
        assertEquals(1, updated.size)
        assertEquals("youtube", updated[0].id)
    }

    @Test
    fun `resetToDefaults restores built-ins and keeps custom`() {
        val custom = DomainList(id = "c", name = "Custom", domains = listOf("c.com"), isBuiltIn = false)
        val modified = listOf(builtIn1.copy(isActive = false), custom)
        val updated = manager.resetToDefaults(modified)
        assertTrue(updated.first { it.id == "general" }.isActive, "reset restores default active state")
        assertTrue(updated.any { it.id == "c" }, "custom list preserved")
        assertEquals(4, updated.size)
    }

    @Test
    fun `save delegates to store`() {
        val lists = listOf(builtIn1)
        manager.save(lists)
        assertEquals(lists, store.saved)
    }

    private class FakeStore : DomainListStore {
        var data: List<DomainList> = emptyList()
        var saved: List<DomainList> = emptyList()
        override fun load(): List<DomainList> = data
        override fun save(lists: List<DomainList>) { saved = lists }
    }
}
