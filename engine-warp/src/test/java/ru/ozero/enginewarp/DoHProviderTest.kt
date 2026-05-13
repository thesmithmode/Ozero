package ru.ozero.enginewarp

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DoHProviderTest {

    @Test
    fun `SYSTEM isSystem true`() {
        assertTrue(DoHProvider.SYSTEM.isSystem)
    }

    @Test
    fun `не SYSTEM isSystem false`() {
        DoHProvider.entries.filter { it != DoHProvider.SYSTEM }.forEach { provider ->
            assertFalse(provider.isSystem, "${provider.name} должен быть isSystem=false")
        }
    }

    @Test
    fun `все не SYSTEM имеют непустой url`() {
        DoHProvider.entries.filter { it != DoHProvider.SYSTEM }.forEach { provider ->
            assertTrue(provider.url.startsWith("https://"), "${provider.name} url должен начинаться с https://")
        }
    }

    @Test
    fun `SYSTEM имеет пустой url`() {
        assertEquals("", DoHProvider.SYSTEM.url)
    }

    @Test
    fun `все провайдеры имеют непустое displayName`() {
        DoHProvider.entries.forEach { provider ->
            assertTrue(provider.displayName.isNotBlank(), "${provider.name} displayName пустой")
        }
    }
}
