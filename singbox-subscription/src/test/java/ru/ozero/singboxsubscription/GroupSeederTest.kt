package ru.ozero.singboxsubscription

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ru.ozero.singboxroom.entity.SubscriptionGroup

class GroupSeederTest {

    private lateinit var fakeDao: FakeSubscriptionGroupDao
    private lateinit var seeder: GroupSeeder

    @BeforeEach
    fun setUp() {
        fakeDao = FakeSubscriptionGroupDao()
        seeder = GroupSeeder(fakeDao)
    }

    @Test
    fun `should insert all presets when db is empty`() = runBlocking {
        val presets = listOf(
            GroupSeeder.PresetGroup("Group A", "https://a.example.com/sub"),
            GroupSeeder.PresetGroup("Group B", "https://b.example.com/sub"),
        )

        seeder.seedPresets(presets)

        assertEquals(2, fakeDao.groups.size)
    }

    @Test
    fun `should mark inserted presets as builtin`() = runBlocking {
        val presets = listOf(
            GroupSeeder.PresetGroup("Preset 1", "https://preset1.example.com/sub"),
        )

        seeder.seedPresets(presets)

        assertTrue(fakeDao.groups.all { it.isBuiltin })
    }

    @Test
    fun `should skip preset when url already exists in db`() = runBlocking {
        fakeDao.groups.add(
            SubscriptionGroup(
                id = 1L,
                name = "Existing",
                subscriptionUrl = "https://a.example.com/sub",
                isBuiltin = true,
            ),
        )
        seeder.seedPresets(listOf(GroupSeeder.PresetGroup("Group A", "https://a.example.com/sub")))

        assertEquals(1, fakeDao.groups.size)
    }

    @Test
    fun `should be idempotent when called twice with same presets`() = runBlocking {
        val presets = listOf(
            GroupSeeder.PresetGroup("Group A", "https://a.example.com/sub"),
            GroupSeeder.PresetGroup("Group B", "https://b.example.com/sub"),
        )

        seeder.seedPresets(presets)
        seeder.seedPresets(presets)

        assertEquals(2, fakeDao.groups.size)
    }

    @Test
    fun `should assign sequential userOrder based on list position`() = runBlocking {
        val presets = listOf(
            GroupSeeder.PresetGroup("A", "https://a.example.com"),
            GroupSeeder.PresetGroup("B", "https://b.example.com"),
            GroupSeeder.PresetGroup("C", "https://c.example.com"),
        )

        seeder.seedPresets(presets)

        val orders = fakeDao.groups.sortedBy { it.userOrder }.map { it.userOrder }
        assertEquals(listOf(0, 1, 2), orders)
    }

    @Test
    fun `should insert new preset while skipping url-duplicate`() = runBlocking {
        fakeDao.groups.add(
            SubscriptionGroup(
                id = 1L,
                name = "Old",
                subscriptionUrl = "https://a.example.com",
                isBuiltin = true,
            ),
        )
        val presets = listOf(
            GroupSeeder.PresetGroup("Old", "https://a.example.com"),
            GroupSeeder.PresetGroup("New", "https://b.example.com"),
        )

        seeder.seedPresets(presets)

        assertEquals(2, fakeDao.groups.size)
        assertTrue(fakeDao.groups.any { it.subscriptionUrl == "https://b.example.com" })
    }

    @Test
    fun `should handle empty preset list without errors`() = runBlocking {
        seeder.seedPresets(emptyList())

        assertEquals(0, fakeDao.groups.size)
    }

    @Test
    fun `should preserve name from preset`() = runBlocking {
        seeder.seedPresets(listOf(GroupSeeder.PresetGroup("My Preset", "https://preset.example.com")))

        assertEquals("My Preset", fakeDao.groups.first().name)
    }

    @Test
    fun `should set subscriptionUrl from preset url`() = runBlocking {
        seeder.seedPresets(listOf(GroupSeeder.PresetGroup("P", "https://url.example.com/path")))

        assertEquals("https://url.example.com/path", fakeDao.groups.first().subscriptionUrl)
    }
}
