package ru.ozero.app.workers

import org.junit.jupiter.api.Test
import ru.ozero.singboxroom.entity.SubscriptionGroup
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SubscriptionUpdateWorkerTest {

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
            autoUpdate = true,
            lastUpdated = 0,
        )

        assertTrue(group.shouldRunSingboxSubscriptionUpdate(now = due))
    }
}
