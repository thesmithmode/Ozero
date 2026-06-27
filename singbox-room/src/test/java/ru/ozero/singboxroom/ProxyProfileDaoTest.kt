package ru.ozero.singboxroom

import androidx.room.Room
import kotlinx.coroutines.flow.first
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
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import ru.ozero.singboxroom.entity.ProxyChainStep
import ru.ozero.singboxroom.entity.ProxyProfile
import ru.ozero.singboxroom.entity.SubscriptionGroup

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ProxyProfileDaoTest {

    private lateinit var db: SingboxDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
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
    fun `limited queries should keep deterministic profile order`() = runBlocking {
        val group1 = insertGroup("Group 1")
        val group2 = insertGroup("Group 2")
        val dao = db.proxyProfileDao()
        val blob = byteArrayOf(0)
        dao.insert(
            ProxyProfile(groupId = group1, name = "G1-B", beanBlob = blob, protocolType = 1, userOrder = 2),
        )
        dao.insert(
            ProxyProfile(groupId = group1, name = "G1-A", beanBlob = blob, protocolType = 1, userOrder = 1),
        )
        dao.insert(
            ProxyProfile(groupId = group2, name = "G2-A", beanBlob = blob, protocolType = 1, userOrder = 0),
        )

        val allLimited = dao.getAllLimitedFlow(2).first()
        val groupLimited = dao.getByGroupIdLimited(group1, 1)

        assertEquals(listOf("G1-A", "G1-B"), allLimited.map { it.name })
        assertEquals(listOf("G1-A"), groupLimited.map { it.name })
    }

    @Test
    fun `auto candidate queries should prefer measured healthy profiles`() = runBlocking {
        val groupId = insertGroup()
        val dao = db.proxyProfileDao()
        val blob = byteArrayOf(0)
        dao.insert(
            ProxyProfile(
                groupId = groupId,
                name = "Untested",
                beanBlob = blob,
                protocolType = 1,
                latencyMs = -1,
                userOrder = 0,
            ),
        )
        dao.insert(
            ProxyProfile(
                groupId = groupId,
                name = "Healthy",
                beanBlob = blob,
                protocolType = 1,
                latencyMs = 42,
                userOrder = 1000,
            ),
        )
        dao.insert(
            ProxyProfile(
                groupId = groupId,
                name = "Failed",
                beanBlob = blob,
                protocolType = 1,
                latencyMs = -2,
                userOrder = 1,
            ),
        )

        val globalCandidates = dao.getAutoCandidatesFlow(2).first()
        val groupCandidates = dao.getAutoCandidatesByGroupId(groupId, 2)

        assertEquals(listOf("Healthy", "Untested"), globalCandidates.map { it.name })
        assertEquals(listOf("Healthy", "Untested"), groupCandidates.map { it.name })
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
    fun `should update probe result for a specific profile`() = runBlocking {
        val groupId = insertGroup()
        val id = db.proxyProfileDao().insert(
            ProxyProfile(groupId = groupId, name = "S1", beanBlob = byteArrayOf(0), protocolType = 1),
        )

        db.proxyProfileDao().updateProbeResult(id, 150, null, 123L)

        val updated = db.proxyProfileDao().getById(id)!!
        assertEquals(150, updated.latencyMs)
        assertNull(updated.probeError)
        assertEquals(123L, updated.lastProbeAt)
    }

    @Test
    fun `should update only probe result columns`() = runBlocking {
        val groupId = insertGroup()
        val dao = db.proxyProfileDao()
        val id = dao.insert(
            ProxyProfile(groupId = groupId, name = "S1", beanBlob = byteArrayOf(0), protocolType = 1, userOrder = 7),
        )

        dao.updateProbeResult(id, -2, "probe failed", 123L)

        val updated = dao.getById(id)!!
        assertEquals("S1", updated.name)
        assertArrayEquals(byteArrayOf(0), updated.beanBlob)
        assertEquals(7, updated.userOrder)
        assertEquals(-2, updated.latencyMs)
        assertEquals("probe failed", updated.probeError)
        assertEquals(123L, updated.lastProbeAt)
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
    fun `insertAll should ignore stable id conflicts without cascading chain steps`() = runBlocking {
        val groupId = insertGroup()
        val profileDao = db.proxyProfileDao()
        val chainDao = db.proxyChainDao()
        val id = profileDao.insert(
            ProxyProfile(groupId = groupId, name = "Stable", beanBlob = byteArrayOf(0), protocolType = 1),
        )
        chainDao.replace(listOf(id))

        profileDao.insertAll(
            listOf(
                ProxyProfile(
                    id = id,
                    groupId = groupId,
                    name = "Conflicting refresh row",
                    beanBlob = byteArrayOf(1),
                    protocolType = 1,
                ),
            ),
        )

        assertEquals(listOf(id), chainDao.getAll().map { it.profileId })
        assertEquals("Stable", profileDao.getById(id)!!.name)
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

    @Test
    fun `replaceForGroup should preserve chain steps for stable profile ids`() = runBlocking {
        val groupId = insertGroup()
        val blob = byteArrayOf(0)
        val profileDao = db.proxyProfileDao()
        val chainDao = db.proxyChainDao()
        val firstId = profileDao.insert(
            ProxyProfile(groupId = groupId, name = "Old 1", beanBlob = blob, protocolType = 1, userOrder = 0),
        )
        val secondId = profileDao.insert(
            ProxyProfile(groupId = groupId, name = "Old 2", beanBlob = blob, protocolType = 1, userOrder = 1),
        )
        chainDao.replace(listOf(firstId, secondId))

        profileDao.replaceForGroup(
            groupId,
            listOf(
                ProxyProfile(
                    id = firstId,
                    groupId = groupId,
                    name = "New 1",
                    beanBlob = byteArrayOf(1),
                    protocolType = 1,
                    userOrder = 0,
                ),
                ProxyProfile(
                    id = secondId,
                    groupId = groupId,
                    name = "New 2",
                    beanBlob = byteArrayOf(2),
                    protocolType = 1,
                    userOrder = 1,
                ),
            ),
        )

        val steps = chainDao.getAll()
        assertEquals(listOf(firstId, secondId), steps.map { it.profileId })
        assertEquals(listOf(0, 1), steps.map { it.userOrder })
        assertEquals(listOf("New 1", "New 2"), profileDao.getByGroupId(groupId).map { it.name })
    }

    @Test
    fun `replaceForGroup should cascade only removed profile chain steps`() = runBlocking {
        val groupId = insertGroup()
        val blob = byteArrayOf(0)
        val profileDao = db.proxyProfileDao()
        val chainDao = db.proxyChainDao()
        val keptId = profileDao.insert(
            ProxyProfile(groupId = groupId, name = "Keep", beanBlob = blob, protocolType = 1, userOrder = 0),
        )
        val removedId = profileDao.insert(
            ProxyProfile(groupId = groupId, name = "Remove", beanBlob = blob, protocolType = 1, userOrder = 1),
        )
        chainDao.replace(listOf(keptId, removedId))

        profileDao.replaceForGroup(
            groupId,
            listOf(
                ProxyProfile(
                    id = keptId,
                    groupId = groupId,
                    name = "Keep refreshed",
                    beanBlob = byteArrayOf(1),
                    protocolType = 1,
                    userOrder = 0,
                ),
            ),
        )

        assertEquals(listOf(keptId), chainDao.getAll().map { it.profileId })
        assertNull(profileDao.getById(removedId))
    }

    @Test
    fun `replaceForGroup should handle large stable id subscriptions without dropping chains`() = runBlocking {
        val groupId = insertGroup()
        val profileDao = db.proxyProfileDao()
        val chainDao = db.proxyChainDao()
        val ids = (1..1007).map { index ->
            profileDao.insert(
                ProxyProfile(
                    groupId = groupId,
                    name = "Old $index",
                    beanBlob = byteArrayOf(index.toByte()),
                    protocolType = 1,
                    userOrder = index,
                ),
            )
        }
        val keptIds = ids.take(1005)
        chainDao.insertAll(
            listOf(
                ProxyChainStep(profileId = keptIds.first(), userOrder = 0),
                ProxyChainStep(profileId = keptIds[999], userOrder = 1),
                ProxyChainStep(profileId = keptIds.last(), userOrder = 2),
            ),
        )

        profileDao.replaceForGroup(
            groupId,
            keptIds.mapIndexed { index, id ->
                ProxyProfile(
                    id = id,
                    groupId = groupId,
                    name = "New ${index + 1}",
                    beanBlob = byteArrayOf((index + 1).toByte()),
                    protocolType = 1,
                    userOrder = index,
                )
            },
        )

        assertEquals(1005, profileDao.countByGroupId(groupId))
        assertNull(profileDao.getById(ids[1005]))
        assertNull(profileDao.getById(ids[1006]))
        assertEquals(listOf(keptIds.first(), keptIds[999], keptIds.last()), chainDao.getAll().map { it.profileId })
    }
}
