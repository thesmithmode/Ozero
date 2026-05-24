package ru.ozero.singboxroom

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ru.ozero.singboxroom.entity.ProxyProfile
import ru.ozero.singboxroom.entity.SubscriptionGroup

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ProxyProfileDaoTest {

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

    private suspend fun insertGroup(name: String = "Test Group"): Long =
        db.subscriptionGroupDao().insert(
            SubscriptionGroup(name = name, subscriptionUrl = "https://example.com/sub"),
        )

    @Test
    fun `should return null when profile not found by id`() = runBlocking {
        assertNull(db.proxyProfileDao().getById(999L))
    }

    @Test
    fun `should insert and retrieve profile by id`() = runBlocking {
        val groupId = insertGroup()
        val blob = byteArrayOf(1, 2, 3, 4)
        val id = db.proxyProfileDao().insert(
            ProxyProfile(groupId = groupId, name = "Server 1", beanBlob = blob, protocolType = 1),
        )

        val retrieved = db.proxyProfileDao().getById(id)
        assertNotNull(retrieved)
        assertEquals("Server 1", retrieved!!.name)
        assertArrayEquals(blob, retrieved.beanBlob)
    }

    @Test
    fun `should return empty list for group with no profiles`() = runBlocking {
        val groupId = insertGroup()
        assertTrue(db.proxyProfileDao().getByGroupId(groupId).isEmpty())
    }

    @Test
    fun `should get profiles by groupId ordered by userOrder then id`() = runBlocking {
        val groupId = insertGroup()
        val dao = db.proxyProfileDao()
        val blob = byteArrayOf(0)
        dao.insert(
            ProxyProfile(groupId = groupId, name = "B", beanBlob = blob, protocolType = 1, userOrder = 2),
        )
        dao.insert(
            ProxyProfile(groupId = groupId, name = "A", beanBlob = blob, protocolType = 1, userOrder = 1),
        )

        val profiles = dao.getByGroupId(groupId)
        assertEquals(2, profiles.size)
        assertEquals("A", profiles[0].name)
        assertEquals("B", profiles[1].name)
    }

    @Test
    fun `should delete all profiles for a group by groupId`() = runBlocking {
        val groupId = insertGroup()
        val dao = db.proxyProfileDao()
        val blob = byteArrayOf(0)
        dao.insert(ProxyProfile(groupId = groupId, name = "S1", beanBlob = blob, protocolType = 1))
        dao.insert(ProxyProfile(groupId = groupId, name = "S2", beanBlob = blob, protocolType = 1))

        dao.deleteByGroupId(groupId)

        assertEquals(0, dao.countByGroupId(groupId))
    }

    @Test
    fun `should cascade delete profiles when group is deleted`() = runBlocking {
        val groupId = insertGroup()
        val blob = byteArrayOf(0)
        db.proxyProfileDao().insert(
            ProxyProfile(groupId = groupId, name = "S1", beanBlob = blob, protocolType = 1),
        )

        db.subscriptionGroupDao().delete(db.subscriptionGroupDao().getById(groupId)!!)

        assertEquals(0, db.proxyProfileDao().countByGroupId(groupId))
    }

    @Test
    fun `should update latency for a specific profile`() = runBlocking {
        val groupId = insertGroup()
        val id = db.proxyProfileDao().insert(
            ProxyProfile(groupId = groupId, name = "S1", beanBlob = byteArrayOf(0), protocolType = 1),
        )

        db.proxyProfileDao().updateLatency(id, 150)

        assertEquals(150, db.proxyProfileDao().getById(id)!!.latencyMs)
    }

    @Test
    fun `should default latency to -1 on insert`() = runBlocking {
        val groupId = insertGroup()
        val id = db.proxyProfileDao().insert(
            ProxyProfile(groupId = groupId, name = "S1", beanBlob = byteArrayOf(0), protocolType = 1),
        )

        assertEquals(-1, db.proxyProfileDao().getById(id)!!.latencyMs)
    }

    @Test
    fun `should count profiles by group excluding other groups`() = runBlocking {
        val group1 = insertGroup("Group 1")
        val group2 = insertGroup("Group 2")
        val blob = byteArrayOf(0)
        db.proxyProfileDao().insert(
            ProxyProfile(groupId = group1, name = "S1", beanBlob = blob, protocolType = 1),
        )
        db.proxyProfileDao().insert(
            ProxyProfile(groupId = group1, name = "S2", beanBlob = blob, protocolType = 1),
        )
        db.proxyProfileDao().insert(
            ProxyProfile(groupId = group2, name = "S3", beanBlob = blob, protocolType = 1),
        )

        assertEquals(2, db.proxyProfileDao().countByGroupId(group1))
        assertEquals(1, db.proxyProfileDao().countByGroupId(group2))
    }

    @Test
    fun `should insert all profiles in batch`() = runBlocking {
        val groupId = insertGroup()
        val blob = byteArrayOf(0)
        val profiles = (1..5).map {
            ProxyProfile(groupId = groupId, name = "Server $it", beanBlob = blob, protocolType = 1)
        }

        db.proxyProfileDao().insertAll(profiles)

        assertEquals(5, db.proxyProfileDao().countByGroupId(groupId))
    }

    @Test
    fun `should not return profiles from other groups`() = runBlocking {
        val group1 = insertGroup("Group 1")
        val group2 = insertGroup("Group 2")
        db.proxyProfileDao().insert(
            ProxyProfile(groupId = group1, name = "S1", beanBlob = byteArrayOf(0), protocolType = 1),
        )

        assertTrue(db.proxyProfileDao().getByGroupId(group2).isEmpty())
    }

    @Test
    fun `should replace profiles when inserting batch for refresh`() = runBlocking {
        val groupId = insertGroup()
        val blob = byteArrayOf(0)
        val dao = db.proxyProfileDao()
        dao.insert(ProxyProfile(groupId = groupId, name = "Old 1", beanBlob = blob, protocolType = 1))
        dao.insert(ProxyProfile(groupId = groupId, name = "Old 2", beanBlob = blob, protocolType = 1))

        dao.deleteByGroupId(groupId)
        val newProfiles = listOf(
            ProxyProfile(groupId = groupId, name = "New 1", beanBlob = blob, protocolType = 1),
        )
        dao.insertAll(newProfiles)

        val result = dao.getByGroupId(groupId)
        assertEquals(1, result.size)
        assertEquals("New 1", result[0].name)
    }
}
