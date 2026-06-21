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
    fun `should skip only exact url duplicates in single call`() = runBlocking {
        fakeDao.groups.add(
            SubscriptionGroup(
                id = 10L,
                name = "Old",
                subscriptionUrl = "https://a.example.com",
                isBuiltin = true,
            ),
        )
        val presets = listOf(
            GroupSeeder.PresetGroup("Old 1", "https://a.example.com"),
            GroupSeeder.PresetGroup("Old 2", "https://a.example.com"),
            GroupSeeder.PresetGroup("New", "https://b.example.com"),
        )

        seeder.seedPresets(presets)

        assertEquals(2, fakeDao.groups.size)
        assertTrue(fakeDao.groups.any { it.subscriptionUrl == "https://b.example.com" })
        assertEquals(1, fakeDao.groups.count { it.subscriptionUrl == "https://a.example.com" })
    }

    @Test
    fun `should preserve original preset position for userOrder after duplicate skip`() = runBlocking {
        val presets = listOf(
            GroupSeeder.PresetGroup("First", "https://same.example.com"),
            GroupSeeder.PresetGroup("Duplicate", "https://same.example.com"),
            GroupSeeder.PresetGroup("Third", "https://third.example.com"),
        )

        seeder.seedPresets(presets)

        assertEquals(2, fakeDao.groups.first { it.subscriptionUrl == "https://third.example.com" }.userOrder)
    }

    @Test
    fun `should remove stale builtin with profiles while preserving user and active builtin`() = runBlocking {
        fakeDao.groups.addAll(
            listOf(
                SubscriptionGroup(
                    id = 1L,
                    name = "Stale Builtin",
                    subscriptionUrl = "https://stale.example.com/sub",
                    isBuiltin = true,
                ),
                SubscriptionGroup(
                    id = 2L,
                    name = "User Group",
                    subscriptionUrl = "https://stale.example.com/sub",
                    isBuiltin = false,
                ),
                SubscriptionGroup(
                    id = 3L,
                    name = "Active Builtin",
                    subscriptionUrl = "https://active.example.com/sub",
                    isBuiltin = true,
                ),
            ),
        )
        fakeDao.profileGroupIds.addAll(listOf(1L, 2L, 3L))

        seeder.seedPresets(listOf(GroupSeeder.PresetGroup("Active Builtin", "https://active.example.com/sub")))

        assertEquals(listOf(2L, 3L), fakeDao.groups.sortedBy { it.id }.map { it.id })
        assertEquals(listOf(2L, 3L), fakeDao.profileGroupIds.sorted())
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

    @Test
    fun `should keep first preset name when duplicate urls appear in the same seed call`() = runBlocking {
        val presets = listOf(
            GroupSeeder.PresetGroup("First Name", "https://dup.example.com"),
            GroupSeeder.PresetGroup("Second Name", "https://dup.example.com"),
        )

        seeder.seedPresets(presets)

        assertEquals(1, fakeDao.groups.size)
        assertEquals("First Name", fakeDao.groups.first().name)
    }

    @Test
    fun `should keep user order of first unseen preset after skipping existing and duplicate urls`() = runBlocking {
        fakeDao.groups.add(
            SubscriptionGroup(
                id = 3L,
                name = "Existing",
                subscriptionUrl = "https://existing.example.com",
                isBuiltin = true,
            ),
        )

        val presets = listOf(
            GroupSeeder.PresetGroup("Existing Duplicate", "https://existing.example.com"),
            GroupSeeder.PresetGroup("New First", "https://new-first.example.com"),
            GroupSeeder.PresetGroup("New Duplicate", "https://new-first.example.com"),
            GroupSeeder.PresetGroup("New Second", "https://new-second.example.com"),
        )

        seeder.seedPresets(presets)

        assertEquals(3, fakeDao.groups.size)
        assertEquals(1, fakeDao.groups.first { it.subscriptionUrl == "https://new-first.example.com" }.userOrder)
        assertEquals(3, fakeDao.groups.first { it.subscriptionUrl == "https://new-second.example.com" }.userOrder)
    }
}
