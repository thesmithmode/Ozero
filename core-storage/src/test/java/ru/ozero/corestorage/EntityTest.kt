package ru.ozero.corestorage

import org.junit.jupiter.api.Test
import ru.ozero.corestorage.entity.AppSplitRule
import ru.ozero.corestorage.entity.ServerEntity
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EntityTest {
    @Test
    fun serverEntityDefaultValues() {
        val server = ServerEntity(
            id = "server-1",
            country = "US",
            role = "entry",
            protocol = "vless",
            uri = "example.com",
            port = 443
        )

        assertEquals("server-1", server.id)
        assertEquals("US", server.country)
        assertEquals("entry", server.role)
        assertEquals("vless", server.protocol)
        assertEquals("example.com", server.uri)
        assertEquals(443, server.port)
        assertEquals(10, server.priority)
        assertTrue(server.isAlive)
        assertEquals(0L, server.lastCheckedAt)
    }

    @Test
    fun serverEntityEquality() {
        val server1 = ServerEntity(
            id = "server-1",
            country = "US",
            role = "entry",
            protocol = "vless",
            uri = "example.com",
            port = 443,
            priority = 5,
            isAlive = true,
            lastCheckedAt = 1000L
        )

        val server2 = ServerEntity(
            id = "server-1",
            country = "US",
            role = "entry",
            protocol = "vless",
            uri = "example.com",
            port = 443,
            priority = 5,
            isAlive = true,
            lastCheckedAt = 1000L
        )

        assertEquals(server1, server2)
    }

    @Test
    fun serverEntityCopy() {
        val original = ServerEntity(
            id = "server-1",
            country = "US",
            role = "entry",
            protocol = "vless",
            uri = "example.com",
            port = 443,
            priority = 10,
            isAlive = true,
            lastCheckedAt = 0L
        )

        val modified = original.copy(priority = 5, isAlive = false)

        assertEquals("server-1", modified.id)
        assertEquals(5, modified.priority)
        assertFalse(modified.isAlive)
        assertEquals(10, original.priority)
        assertTrue(original.isAlive)
    }

    @Test
    fun appSplitRuleDefaultExcluded() {
        val rule = AppSplitRule(packageName = "com.example.app")

        assertEquals("com.example.app", rule.packageName)
        assertFalse(rule.isExcluded)
    }
}
