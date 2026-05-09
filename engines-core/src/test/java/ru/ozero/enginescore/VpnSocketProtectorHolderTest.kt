package ru.ozero.enginescore

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VpnSocketProtectorHolderTest {

    private var bound: VpnSocketProtector? = null

    @AfterEach
    fun tearDown() {
        bound?.let { VpnSocketProtectorHolder.unbind(it) }
        bound = null
    }

    private fun bind(protector: VpnSocketProtector): VpnSocketProtector {
        VpnSocketProtectorHolder.bind(protector)
        bound = protector
        return protector
    }

    @Test
    fun `protect returns false when no protector bound`() {
        assertFalse(VpnSocketProtectorHolder.protect(42))
    }

    @Test
    fun `protect delegates to bound protector`() {
        val captured = mutableListOf<Int>()
        bind(VpnSocketProtector { fd -> captured += fd; true })

        val result = VpnSocketProtectorHolder.protect(7)

        assertTrue(result)
        assertEquals(listOf(7), captured)
    }

    @Test
    fun `protect returns false after unbind`() {
        val protector = bind(VpnSocketProtector { true })
        VpnSocketProtectorHolder.unbind(protector)
        bound = null

        assertFalse(VpnSocketProtectorHolder.protect(1))
    }

    @Test
    fun `unbind with wrong protector leaves original intact`() {
        val original = bind(VpnSocketProtector { true })
        val impostor = VpnSocketProtector { false }

        VpnSocketProtectorHolder.unbind(impostor)

        assertTrue(VpnSocketProtectorHolder.protect(1), "оригинальный protector остаётся — unbind по identity (===)")
        bound = original
    }

    @Test
    fun `bind replaces previous protector`() {
        val first = bind(VpnSocketProtector { false })
        val second = VpnSocketProtector { true }
        VpnSocketProtectorHolder.bind(second)
        bound = second

        assertTrue(VpnSocketProtectorHolder.protect(1), "второй bind заменяет первый")
        VpnSocketProtectorHolder.unbind(second)
        VpnSocketProtectorHolder.unbind(first)
        bound = null
    }

    @Test
    fun `protect returns false when bound protector returns false`() {
        bind(VpnSocketProtector { false })

        assertFalse(VpnSocketProtectorHolder.protect(99))
    }
}
