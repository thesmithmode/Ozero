package ru.ozero.security.antifrida

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AntiFridaCheckTest {

    private fun check(maps: String?) = AntiFridaCheck(reader = { maps })

    @Test
    fun emptyMapsReturnsFalse() = assertFalse(check("").isHookFrameworkPresent())

    @Test
    fun nullMapsReturnsFalse() = assertFalse(check(null).isHookFrameworkPresent())

    @Test
    fun cleanMapsReturnsFalse() {
        val clean = """
            7f0000000000-7f0000001000 r--p 00000000 fc:00 1234 /system/lib/libc.so
            7f0000001000-7f0000002000 r-xp 00000000 fc:00 1235 /system/lib/libstdc++.so
        """.trimIndent()
        assertFalse(check(clean).isHookFrameworkPresent())
    }

    @Test
    fun fridaAgentDetected() {
        val tainted = "7f00-7f01 r-xp 00 00 1 /data/local/tmp/frida-agent-arm64.so"
        assertTrue(check(tainted).isHookFrameworkPresent())
        assertEquals("frida-agent", check(tainted).firstSignatureMatch())
    }

    @Test
    fun fridaGadgetDetected() {
        val tainted = "7f00-7f01 r-xp 00 00 1 /data/app/x/lib/arm64/frida-gadget.so"
        assertTrue(check(tainted).isHookFrameworkPresent())
    }

    @Test
    fun xposedDetected() {
        val tainted = "7f00-7f01 r--p 00 00 1 /system/framework/XposedBridge.jar"
        assertTrue(check(tainted).isHookFrameworkPresent())
    }

    @Test
    fun lsposedDetected() {
        val tainted = "7f00-7f01 r--p 00 00 1 /apex/com.android.runtime/javalib/LSPosed.dex"
        assertTrue(check(tainted).isHookFrameworkPresent())
    }

    @Test
    fun magiskDetected() {
        val tainted = "7f00-7f01 r-xp 00 00 1 /sbin/.magisk/mirror/system/bin/sh"
        assertTrue(check(tainted).isHookFrameworkPresent())
    }

    @Test
    fun substrateDetected() {
        val tainted = "7f00 r-xp 0 0 1 /data/local/tmp/cydia.substrate.dylib"
        assertTrue(check(tainted).isHookFrameworkPresent())
    }

    @Test
    fun firstMatchReturnsNullForCleanMaps() {
        assertNull(check("7f00 r-xp 0 0 1 /system/lib/libc.so").firstSignatureMatch())
    }

    @Test
    fun caseInsensitiveMatch() {
        assertTrue(check("FRIDA-AGENT").isHookFrameworkPresent())
    }
}
