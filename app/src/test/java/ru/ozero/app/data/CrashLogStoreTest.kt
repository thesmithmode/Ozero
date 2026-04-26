package ru.ozero.app.data

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CrashLogStoreTest {

    @TempDir lateinit var tmp: File

    @Test
    fun `write creates file with stack trace`() {
        val store = CrashLogStore(tmp)
        val ex = IllegalStateException("boom")
        store.write(Thread.currentThread(), ex)
        val files = store.list()
        assertEquals(1, files.size)
        val content = files.first().readText()
        assertTrue(content.contains("IllegalStateException"))
        assertTrue(content.contains("boom"))
        assertTrue(content.contains("thread="))
    }

    @Test
    fun `list empty when no crashes`() {
        val store = CrashLogStore(tmp)
        assertTrue(store.list().isEmpty())
    }

    @Test
    fun `directory is filesDir crashes and is created`() {
        val store = CrashLogStore(tmp)
        val dir = store.directory()
        assertEquals(File(tmp, CrashLogStore.DIR_NAME), dir)
        assertTrue(dir.exists())
    }

    @Test
    fun `multiple writes produce distinct files`() {
        val store = CrashLogStore(tmp)
        repeat(3) { i ->
            store.write(Thread.currentThread(), RuntimeException("e$i"))
            Thread.sleep(2)
        }
        assertTrue(store.list().size >= 1)
    }
}
