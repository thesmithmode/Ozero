package ru.ozero.corestorage

import android.content.Context
import androidx.room.Room
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import ru.ozero.corestorage.dao.SessionStatsDao
import ru.ozero.corestorage.entity.SessionStatsEntity
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class SessionStatsDaoActiveFilterTest {

    private lateinit var db: OzeroDatabase
    private lateinit var dao: SessionStatsDao

    @Before
    fun setUp() {
        val ctx: Context = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(ctx, OzeroDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.sessionStatsDao()
    }

    @After
    fun tearDown() {
        runCatching { db.close() }
    }

    @Test
    fun `observeRecent скрывает активную сессию endedAt IS NULL`() = runTest {
        dao.insertStart(running(engineId = "BYEDPI", startedAt = 1_000L))
        dao.insertStart(finished(engineId = "WARP", startedAt = 2_000L, endedAt = 3_000L))

        val items = dao.observeRecent(limit = 30).first()

        assertEquals(1, items.size, "активная сессия (endedAt=null) обязана быть скрыта из истории")
        assertEquals("WARP", items[0].engineId)
        assertTrue(items.all { it.endedAt != null }, "ни одна сессия в истории не должна быть активной")
    }

    @Test
    fun `observeRecent пуст когда есть только активная сессия`() = runTest {
        dao.insertStart(running(engineId = "BYEDPI", startedAt = 1_000L))

        val items = dao.observeRecent(limit = 30).first()

        assertTrue(items.isEmpty(), "если все сессии активны — история обязана быть пустой, не показывать нули")
    }

    @Test
    fun `observeRecent возвращает завершённые в порядке startedAt DESC`() = runTest {
        dao.insertStart(finished(engineId = "A", startedAt = 1_000L, endedAt = 1_500L))
        dao.insertStart(finished(engineId = "B", startedAt = 3_000L, endedAt = 3_500L))
        dao.insertStart(running(engineId = "ACTIVE", startedAt = 5_000L))
        dao.insertStart(finished(engineId = "C", startedAt = 2_000L, endedAt = 2_500L))

        val items = dao.observeRecent(limit = 30).first()

        assertEquals(listOf("B", "C", "A"), items.map { it.engineId })
    }

    @Test
    fun `observeRecent респектит limit и активную не считает в quote`() = runTest {
        dao.insertStart(finished(engineId = "X1", startedAt = 1_000L, endedAt = 1_500L))
        dao.insertStart(finished(engineId = "X2", startedAt = 2_000L, endedAt = 2_500L))
        dao.insertStart(running(engineId = "ACTIVE", startedAt = 3_000L))
        dao.insertStart(finished(engineId = "X3", startedAt = 4_000L, endedAt = 4_500L))

        val items = dao.observeRecent(limit = 2).first()

        assertEquals(2, items.size)
        assertEquals(listOf("X3", "X2"), items.map { it.engineId })
    }

    @Test
    fun `updateEnd конвертирует активную сессию в видимую для observeRecent`() = runTest {
        val id = dao.insertStart(running(engineId = "BYEDPI", startedAt = 1_000L))

        val before = dao.observeRecent(limit = 30).first()
        assertTrue(before.isEmpty(), "активная сессия не видна до updateEnd")

        dao.updateEnd(
            id = id,
            endedAt = 2_000L,
            rxBytes = 100L,
            txBytes = 200L,
            durationMs = 1_000L,
            finalStatus = SessionStatsEntity.STATUS_DISCONNECTED,
        )

        val after = dao.observeRecent(limit = 30).first()
        assertEquals(1, after.size, "после updateEnd сессия обязана появиться в истории")
        assertEquals("BYEDPI", after[0].engineId)
        assertEquals(2_000L, after[0].endedAt)
    }

    private fun running(engineId: String, startedAt: Long) = SessionStatsEntity(
        engineId = engineId,
        startedAt = startedAt,
        endedAt = null,
        finalStatus = SessionStatsEntity.STATUS_RUNNING,
    )

    private fun finished(engineId: String, startedAt: Long, endedAt: Long) = SessionStatsEntity(
        engineId = engineId,
        startedAt = startedAt,
        endedAt = endedAt,
        rxBytes = 100L,
        txBytes = 200L,
        durationMs = endedAt - startedAt,
        finalStatus = SessionStatsEntity.STATUS_DISCONNECTED,
    )
}
