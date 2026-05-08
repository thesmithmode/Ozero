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
    fun `H-значения принимают полный диапазон uint32`() {
        val p = AwgParams(
            initPacketMagicHeader = 0xFFFFFFFFL,
            responsePacketMagicHeader = 0xFFFFFFFEL,
            cookieReplyMagicHeader = 0xFFFFFFFDL,
            transportMagicHeader = 0xFFFFFFFCL,
        )
        assertEquals(0xFFFFFFFFL, p.initPacketMagicHeader)
        assertEquals(0xFFFFFFFEL, p.responsePacketMagicHeader)
    }

    @Test
    fun `H-значения вне диапазона uint32 бросают IllegalArgumentException`() {
        assertFailsWith<IllegalArgumentException> {
            AwgParams(
                initPacketMagicHeader = 0x1_0000_0000L,
                responsePacketMagicHeader = 2L,
                cookieReplyMagicHeader = 3L,
                transportMagicHeader = 4L,
            )
        }
    }

    @Test
    fun `H-значение ноль запрещено (валидные Magic headers неотрицательны и ненулевые)`() {
        assertFailsWith<IllegalArgumentException> {
            AwgParams(
                initPacketMagicHeader = 0L,
                responsePacketMagicHeader = 2L,
                cookieReplyMagicHeader = 3L,
                transportMagicHeader = 4L,
            )
        }
    }

    @Test
    fun `дублирующиеся Magic headers запрещены (collision = AWG protocol violation)`() {
        assertFailsWith<IllegalArgumentException> {
            AwgParams(
                initPacketMagicHeader = 1L,
                responsePacketMagicHeader = 1L,
                cookieReplyMagicHeader = 3L,
                transportMagicHeader = 4L,
            )
        }
    }

    @Test
    fun `Jc вне диапазона 0_128 бросает IllegalArgumentException`() {
        assertFailsWith<IllegalArgumentException> {
            AwgParams(junkPacketCount = 200)
        }
        assertFailsWith<IllegalArgumentException> {
            AwgParams(junkPacketCount = -1)
        }
    }

    @Test
    fun `Jmax вне диапазона 0_1500 бросает IllegalArgumentException`() {
        assertFailsWith<IllegalArgumentException> {
            AwgParams(junkPacketMinSize = 0, junkPacketMaxSize = 2000)
        }
    }

    @Test
    fun `S1 вне диапазона 0_1500 бросает IllegalArgumentException`() {
        assertFailsWith<IllegalArgumentException> {
            AwgParams(initPacketJunkSize = 9999)
        }
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
