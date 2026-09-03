package ru.ozero.singboxsubscription

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ru.ozero.singboxroom.entity.SubscriptionGroup

class GroupSeederPresetSyncTest {
    @Test
    fun `existing builtin with retained url adopts current preset metadata`() = runBlocking {
        val dao = FakeSubscriptionGroupDao()
        dao.groups += SubscriptionGroup(
            id = 7L,
            name = "Zieng WL Universal",
            subscriptionUrl = ZENG2_URL,
            isBuiltin = true,
            autoUpdate = false,
            userOrder = 12,
        )

        GroupSeeder(dao).seedPresets(listOf(GroupSeeder.PresetGroup("zeng2", ZENG2_URL)))

        val group = dao.groups.single()
        assertEquals(7L, group.id)
        assertEquals("zeng2", group.name)
        assertEquals(0, group.userOrder)
        assertTrue(group.autoUpdate)
    }

    @Test
    fun `user group sharing preset url keeps user metadata`() = runBlocking {
        val dao = FakeSubscriptionGroupDao()
        dao.groups += SubscriptionGroup(
            id = 9L,
            name = "My custom name",
            subscriptionUrl = ZENG2_URL,
            isBuiltin = false,
            autoUpdate = false,
            userOrder = 5,
        )

        GroupSeeder(dao).seedPresets(listOf(GroupSeeder.PresetGroup("zeng2", ZENG2_URL)))

        val group = dao.groups.single()
        assertEquals("My custom name", group.name)
        assertEquals(5, group.userOrder)
        assertTrue(!group.autoUpdate)
    }

    private companion object {
        const val ZENG2_URL = "https://raw.githubusercontent.com/zieng2/wl/main/vless_universal.txt"
    }
}
