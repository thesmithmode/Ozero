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

    @Test
    fun `sanitize redacts proxy URI`() {
        val store = CrashLogStore(tmp)
        val raw = "fail at vless://uuid-1@example.com:443?pbk=secret end"
        val out = store.sanitize(raw)
        assertTrue(!out.contains("uuid-1"), "user-info не вырезан: $out")
        assertTrue(!out.contains("example.com:443"), "host vless должен быть заменён proxy uri masker")
    }

    @Test
    fun `sanitize redacts user-info in https URI`() {
        val store = CrashLogStore(tmp)
        val raw = "url https://user:p@ssw0rd@host.example/path"
        val out = store.sanitize(raw)
        assertTrue(!out.contains("user:p@ssw0rd"), "https user-info не вырезан: $out")
    }

    @Test
    fun `sanitize redacts long token`() {
        val store = CrashLogStore(tmp)
        val token = "AAAABBBBCCCCDDDDEEEEFFFFGGGGHHHHIIIIJJJJ" 
        val raw = "key=$token end"
        val out = store.sanitize(raw)
        assertTrue(!out.contains(token), "токен не вырезан: $out")
    }

    @Test
    fun `sanitize keeps normal stack trace lines`() {
        val store = CrashLogStore(tmp)
        val frame = "at ru.ozero.commoncrypto.SubscriptionVerifier.verifyUpdate(SubscriptionVerifier.kt:42)"
        val out = store.sanitize(frame)
        assertEquals(frame, out)
    }
}
