package ru.ozero.enginewarp

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AwgParamsTest {

    @Test
    fun `default AwgParams создаётся без исключений`() {
        val p = AwgParams()
        assertEquals(AwgParams.DEFAULT_JC, p.junkPacketCount)
        assertEquals(AwgParams.DEFAULT_JMIN, p.junkPacketMinSize)
        assertEquals(AwgParams.DEFAULT_JMAX, p.junkPacketMaxSize)
    }

    @Test
    fun `Jmin равный Jmax допустим`() {
        val p = AwgParams(junkPacketMinSize = 100, junkPacketMaxSize = 100)
        assertEquals(100, p.junkPacketMinSize)
        assertEquals(100, p.junkPacketMaxSize)
    }

    @Test
    fun `Jmin больше Jmax бросает IllegalArgumentException`() {
        assertFailsWith<IllegalArgumentException> {
            AwgParams(junkPacketMinSize = 200, junkPacketMaxSize = 100)
        }
    }

    @Test
    fun `граничный случай Jmin=0 Jmax=0 допустим`() {
        val p = AwgParams(junkPacketMinSize = 0, junkPacketMaxSize = 0)
        assertEquals(0, p.junkPacketMinSize)
    }

    @Test
    fun `H-значения типа Long не переполняются при Int max`() {
        val p = AwgParams(
            initPacketMagicHeader = Int.MAX_VALUE.toLong() + 1,
            responsePacketMagicHeader = Long.MAX_VALUE,
        )
        assertEquals(Int.MAX_VALUE.toLong() + 1, p.initPacketMagicHeader)
        assertEquals(Long.MAX_VALUE, p.responsePacketMagicHeader)
    }

    @Test
    fun `VANILLA constant равна vanilla WG baseline (Jc-Jmin-Jmax-S1-S2 = 0, H1-H4 = 1-2-3-4)`() {
        val v = AwgParams.VANILLA
        assertEquals(0, v.junkPacketCount)
        assertEquals(0, v.junkPacketMinSize)
        assertEquals(0, v.junkPacketMaxSize)
        assertEquals(0, v.initPacketJunkSize)
        assertEquals(0, v.responsePacketJunkSize)
        assertEquals(1L, v.initPacketMagicHeader)
        assertEquals(2L, v.responsePacketMagicHeader)
        assertEquals(3L, v.cookieReplyMagicHeader)
        assertEquals(4L, v.transportMagicHeader)
    }

    @Test
    fun `default AwgParams не равно VANILLA (default включает AWG-обфускацию)`() {
        assertEquals(false, AwgParams() == AwgParams.VANILLA)
    }
}
