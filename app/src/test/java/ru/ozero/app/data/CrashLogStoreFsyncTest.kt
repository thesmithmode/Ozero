package ru.ozero.app.data

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CrashLogStoreFsyncTest {

    @TempDir lateinit var tmp: File

    @Test
    fun `write создаёт файл с stacktrace`() {
        val store = CrashLogStore(tmp)
        store.write(Thread.currentThread(), Throwable("test"))
        val files = store.list()
        assertEquals(1, files.size)
        val first = files.first()
        assertTrue(first.exists())
        val text = first.readText()
        assertTrue(text.contains("test"))
        assertTrue(text.contains("thread="))
        assertTrue(text.contains("at "))
    }

    @Test
    fun `sanitize удаляет userinfo URI`() {
        val store = CrashLogStore(tmp)
        val ex = RuntimeException("connect to vless://user:pass@host.example:443 failed")
        store.write(Thread.currentThread(), ex)
        val text = store.list().first().readText()
        assertTrue(!text.contains("user:pass"), "userinfo не вырезан: $text")
    }

    @Test
    fun `rotate удаляет старые файлы свыше MAX_FILES`() {
        val store = CrashLogStore(tmp)
        repeat(11) { i ->
            store.write(Thread.currentThread(), RuntimeException("e$i"))
            Thread.sleep(2)
        }
        assertEquals(CrashLogStore.MAX_FILES, store.list().size)
    }
}
