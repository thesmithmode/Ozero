package ru.ozero.enginescore

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class PersistentLoggersTest {

    @AfterEach
    fun tearDown() {
        PersistentLoggers.instance = null
    }

    @Test
    fun `delegates every level to installed logger`() {
        val logger = RecordingLogger()
        PersistentLoggers.instance = logger
        val cause = IllegalStateException("boom")

        PersistentLoggers.trace("T", "trace")
        PersistentLoggers.debug("T", "debug")
        PersistentLoggers.info("T", "info")
        PersistentLoggers.warn("T", "warn", cause)
        PersistentLoggers.error("T", "error", cause)

        assertEquals(
            listOf(
                "trace:T:trace:null",
                "debug:T:debug:null",
                "info:T:info:null",
                "warn:T:warn:boom",
                "error:T:error:boom",
            ),
            logger.events,
        )
    }

    @Test
    fun `fallback logger paths do not throw when persistent logger is absent`() {
        PersistentLoggers.instance = null
        val cause = IllegalStateException("boom")

        PersistentLoggers.trace("T", "trace")
        PersistentLoggers.debug("T", "debug")
        PersistentLoggers.info("T", "info")
        PersistentLoggers.warn("T", "warn")
        PersistentLoggers.warn("T", "warn", cause)
        PersistentLoggers.error("T", "error")
        PersistentLoggers.error("T", "error", cause)

        assertEquals(null, PersistentLoggers.instance)
    }

    private class RecordingLogger : PersistentLogger {
        val events = mutableListOf<String>()

        override fun trace(tag: String, msg: String) {
            events += "trace:$tag:$msg:null"
        }

        override fun debug(tag: String, msg: String) {
            events += "debug:$tag:$msg:null"
        }

        override fun info(tag: String, msg: String) {
            events += "info:$tag:$msg:null"
        }

        override fun warn(tag: String, msg: String, t: Throwable?) {
            events += "warn:$tag:$msg:${t?.message}"
        }

        override fun error(tag: String, msg: String, t: Throwable?) {
            events += "error:$tag:$msg:${t?.message}"
        }
    }
}
