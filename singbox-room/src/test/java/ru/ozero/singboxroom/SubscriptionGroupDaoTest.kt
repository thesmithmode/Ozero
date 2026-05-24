package ru.ozero.singboxroom

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ru.ozero.singboxroom.entity.SubscriptionGroup

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SubscriptionGroupDaoTest {

    private lateinit var db: SingboxDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            SingboxDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `should return null when group not found by id`() = runBlocking {
        val result = db.subscriptionGroupDao().getById(999L)
        assertNull(result)
    }

    @Test
    fun `should insert and retrieve subscription group by id`() = runBlocking {
        val group = SubscriptionGroup(
            name = "Test Group",
            subscriptionUrl = "https://example.com/sub",
        )
        val id = db.subscriptionGroupDao().insert(group)

        val retrieved = db.subscriptionGroupDao().getById(id)
        assertNotNull(retrieved)
        assertEquals("Test Group", retrieved!!.name)
        assertEquals("https://example.com/sub", retrieved.subscriptionUrl)
    }

    @Test
    fun `should return empty list when no groups exist`() = runBlocking {
        val result = db.subscriptionGroupDao().getAll()
        assertTrue(result.isEmpty())
    }

    @Test
    fun `should return groups ordered by userOrder then id`() = runBlocking {
        val dao = db.subscriptionGroupDao()
        dao.insert(SubscriptionGroup(name = "B", userOrder = 2))
        dao.insert(SubscriptionGroup(name = "A", userOrder = 1))
        dao.insert(SubscriptionGroup(name = "C", userOrder = 2))

        val all = dao.getAll()
        assertEquals(3, all.size)
        assertEquals("A", all[0].name)
    }

    @Test
    fun `should return null when url not found`() = runBlocking {
        val result = db.subscriptionGroupDao().getByUrl("https://notfound.example.com")
        assertNull(result)
    }

    @Test
    fun `should find group by subscription url`() = runBlocking {
        val dao = db.subscriptionGroupDao()
        dao.insert(SubscriptionGroup(name = "Sub1", subscriptionUrl = "https://sub.example.com"))

        val found = dao.getByUrl("https://sub.example.com")
        assertNotNull(found)
        assertEquals("Sub1", found!!.name)
    }

    @Test
    fun `should return only builtin groups`() = runBlocking {
        val dao = db.subscriptionGroupDao()
        dao.insert(SubscriptionGroup(name = "User Group", isBuiltin = false))
        dao.insert(SubscriptionGroup(name = "Preset 1", isBuiltin = true))
        dao.insert(SubscriptionGroup(name = "Preset 2", isBuiltin = true))

        val builtins = dao.getBuiltins()
        assertEquals(2, builtins.size)
        assertTrue(builtins.all { it.isBuiltin })
    }

    @Test
    fun `should update group name`() = runBlocking {
        val dao = db.subscriptionGroupDao()
        val id = dao.insert(SubscriptionGroup(name = "Old Name"))
        val group = dao.getById(id)!!

        dao.update(group.copy(name = "New Name"))

        assertEquals("New Name", dao.getById(id)!!.name)
    }

    @Test
    fun `should delete group`() = runBlocking {
        val dao = db.subscriptionGroupDao()
        val id = dao.insert(SubscriptionGroup(name = "To Delete"))
        dao.delete(dao.getById(id)!!)

        assertNull(dao.getById(id))
    }

    @Test
    fun `should count groups correctly`() = runBlocking {
        val dao = db.subscriptionGroupDao()
        assertEquals(0, dao.count())
        dao.insert(SubscriptionGroup(name = "G1"))
        dao.insert(SubscriptionGroup(name = "G2"))
        assertEquals(2, dao.count())
    }

    @Test
    fun `should allow multiple groups with same url for seeder idempotency via getByUrl check`() =
        runBlocking {
            val dao = db.subscriptionGroupDao()
            dao.insert(SubscriptionGroup(name = "A", subscriptionUrl = "https://shared.example.com"))
            dao.insert(SubscriptionGroup(name = "B", subscriptionUrl = "https://shared.example.com"))
            assertEquals(2, dao.count())
        }

    @Test
    fun `should preserve default field values after insert`() = runBlocking {
        val dao = db.subscriptionGroupDao()
        val id = dao.insert(SubscriptionGroup(name = "Defaults"))

        val g = dao.getById(id)!!
        assertEquals("", g.subscriptionUrl)
        assertEquals(false, g.isBuiltin)
        assertEquals(true, g.autoUpdate)
        assertEquals(360, g.autoUpdateDelay)
        assertEquals(0L, g.lastUpdated)
        assertEquals(0, g.userOrder)
    }
}
