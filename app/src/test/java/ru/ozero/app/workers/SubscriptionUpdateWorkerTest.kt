package ru.ozero.app.workers

import org.junit.jupiter.api.Test
import ru.ozero.singboxroom.entity.SubscriptionGroup
import ru.ozero.singboxsubscription.isTransientSubscriptionRefreshFailure
import java.io.File
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SubscriptionUpdateWorkerTest {

    @Test
    fun `worker retry classification accepts transient network failures only`() {
        assertTrue(isTransientSubscriptionRefreshFailure(SocketTimeoutException("timeout")))
        assertTrue(isTransientSubscriptionRefreshFailure(IOException("connection reset")))
        assertFalse(isTransientSubscriptionRefreshFailure(IllegalArgumentException("unsupported")))
    }

    @Test
    fun `auto update predicate skips local manual groups without subscription url`() {
        val due = 24L * 60 * 60 * 1000
        val group = SubscriptionGroup(
            name = "Manual",
            subscriptionUrl = "",
            autoUpdate = true,
            lastUpdated = 0,
        )

        assertFalse(group.shouldRunSingboxSubscriptionUpdate(now = due))
    }

    @Test
    fun `auto update predicate accepts due remote subscription`() {
        val due = 24L * 60 * 60 * 1000
        val group = SubscriptionGroup(
            name = "Remote",
            subscriptionUrl = "https://example.com/sub",
            lastUpdated = 0,
        )

        assertTrue(group.shouldRunSingboxSubscriptionUpdate(now = due))
    }

    @Test
    fun `auto update predicate uses individual delay`() {
        val group = SubscriptionGroup(
            name = "Remote",
            subscriptionUrl = "https://example.com/sub",
            autoUpdateDelay = 60,
            lastUpdated = 1L,
            lastAttemptAt = 1L,
        )

        assertFalse(group.shouldRunSingboxSubscriptionUpdate(now = TimeUnit.MINUTES.toMillis(59)))
        assertTrue(group.shouldRunSingboxSubscriptionUpdate(now = TimeUnit.MINUTES.toMillis(61)))
    }

    @Test
    fun `auto update interval clamps unsafe values`() {
        val tooShort = SubscriptionGroup(name = "Short", autoUpdateDelay = Int.MIN_VALUE)
        val tooLong = SubscriptionGroup(name = "Long", autoUpdateDelay = Int.MAX_VALUE)

        assertEquals(
            TimeUnit.MINUTES.toMillis(SubscriptionUpdateWorker.MIN_UPDATE_DELAY_MINUTES),
            tooShort.singboxSubscriptionUpdateIntervalMs(),
        )
        assertEquals(
            TimeUnit.MINUTES.toMillis(SubscriptionUpdateWorker.MAX_UPDATE_DELAY_MINUTES),
            tooLong.singboxSubscriptionUpdateIntervalMs(),
        )
    }

    @Test
    fun `failed refresh is throttled until retry delay elapses`() {
        val now = TimeUnit.HOURS.toMillis(1)
        val group = SubscriptionGroup(
            name = "Remote",
            subscriptionUrl = "https://example.com/sub",
            lastAttemptAt = now - TimeUnit.MINUTES.toMillis(14),
        )

        assertFalse(group.shouldRunSingboxSubscriptionUpdate(now))
        assertTrue(
            group.shouldRunSingboxSubscriptionUpdate(
                now + TimeUnit.MINUTES.toMillis(1),
            ),
        )
    }

    @Test
    fun `subscription update worker does not run background singbox probes`() {
        val source = File(
            System.getProperty("user.dir") ?: ".",
            "src/main/java/ru/ozero/app/workers/SubscriptionUpdateWorker.kt",
        ).readText()

        assertFalse(source.contains("SingboxProbeService"))
        assertFalse(source.contains("probeAndAutoSelect"))
        assertFalse(source.contains("getAutoCandidatesByGroupId"))
    }

    @Test
    fun `subscription update worker schedules immediate and periodic work`() {
        val source = File(
            System.getProperty("user.dir") ?: ".",
            "src/main/java/ru/ozero/app/workers/SubscriptionUpdateWorker.kt",
        ).readText()

        assertTrue(source.contains("OneTimeWorkRequestBuilder<SubscriptionUpdateWorker>"))
        assertTrue(source.contains("ExistingPeriodicWorkPolicy.UPDATE"))
        assertTrue(source.contains("enqueueUniqueWork"))
        assertTrue(source.contains("enqueueUniquePeriodicWork"))
    }
}
