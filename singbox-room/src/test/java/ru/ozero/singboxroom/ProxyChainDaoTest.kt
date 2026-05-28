package ru.ozero.singboxroom

import androidx.room.Room
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import ru.ozero.singboxroom.entity.ProxyProfile
import ru.ozero.singboxroom.entity.SubscriptionGroup

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ProxyChainDaoTest {

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

    @Test
    fun `replace stores ordered profile ids`() = runBlocking {
        val groupId = db.subscriptionGroupDao().insert(SubscriptionGroup(name = "G"))
        val first = db.proxyProfileDao().insert(makeProfile(groupId, "RU"))
        val second = db.proxyProfileDao().insert(makeProfile(groupId, "EU"))

        db.proxyChainDao().replace(listOf(first, second))

        val steps = db.proxyChainDao().getAll()
        assertEquals(listOf(first, second), steps.map { it.profileId })
        assertEquals(listOf(0, 1), steps.map { it.userOrder })
    }

    @Test
    fun `profile delete removes chain step`() = runBlocking {
        val groupId = db.subscriptionGroupDao().insert(SubscriptionGroup(name = "G"))
        val profile = db.proxyProfileDao().insert(makeProfile(groupId, "RU"))
        db.proxyChainDao().replace(listOf(profile))

        db.proxyProfileDao().delete(requireNotNull(db.proxyProfileDao().getById(profile)))

        assertEquals(emptyList<Long>(), db.proxyChainDao().getAll().map { it.profileId })
    }

    private fun makeProfile(groupId: Long, name: String): ProxyProfile =
        ProxyProfile(
            groupId = groupId,
            name = name,
            beanBlob = byteArrayOf(name.length.toByte()),
            protocolType = 0,
        )
}
