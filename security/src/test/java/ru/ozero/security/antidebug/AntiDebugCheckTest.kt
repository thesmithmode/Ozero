package ru.ozero.security.antidebug

import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AntiDebugCheckTest {

    private fun checker(
        status: String?,
        javaDebugger: Boolean = false,
        release: Boolean = true,
        tracerPidEnabled: Boolean = true,
    ) =
        AntiDebugCheck(
            reader = { status },
            javaDebuggerAttached = { javaDebugger },
            isReleaseBuild = { release },
            tracerPidEnabled = tracerPidEnabled,
        )

    private val cleanStatus = """
        Name: ozero
        State: S (sleeping)
        Tgid: 1234
        Pid: 1234
        TracerPid: 0
    """.trimIndent()

    private val tracedStatus = """
        Name: ozero
        TracerPid: 9999
    """.trimIndent()

    @Test
    fun cleanStatusReturnsFalse() {
        assertFalse(checker(cleanStatus).tracerPidNonZero())
        assertFalse(checker(cleanStatus).isDebuggerAttached())
    }

    @Test
    fun tracedStatusReturnsTrue() {
        assertTrue(checker(tracedStatus).tracerPidNonZero())
        assertTrue(checker(tracedStatus, tracerPidEnabled = true).isDebuggerAttached())
    }

    @Test
    fun tracerPidIgnoredWhenDisabled() {
        assertFalse(checker(tracedStatus, tracerPidEnabled = false).isDebuggerAttached())
    }

    @Test
    fun missingStatusFileReturnsFalse() {
        assertFalse(checker(null).tracerPidNonZero())
    }

    @Test
    fun malformedTracerPidLineReturnsFalse() {
        assertFalse(checker("TracerPid: abc").tracerPidNonZero())
    }

    @Test
    fun javaDebuggerInReleaseTriggers() {
        assertTrue(checker(cleanStatus, javaDebugger = true, release = true).isDebuggerAttached())
    }

    @Test
    fun javaDebuggerInDebugIgnored() {
        assertFalse(checker(cleanStatus, javaDebugger = true, release = false).isDebuggerAttached())
    }
}
