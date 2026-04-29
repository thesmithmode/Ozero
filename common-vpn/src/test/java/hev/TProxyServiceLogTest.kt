package hev

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertTrue

class TProxyServiceLogTest {

    private val source by lazy {
        val moduleRoot = File(System.getProperty("user.dir") ?: ".")
        val f = File(moduleRoot, "src/main/java/hev/TProxyService.kt")
        assertTrue(f.exists(), "TProxyService.kt не найден: $f")
        f.readText()
    }

    @Test
    fun `init логирует loadLibrary begin до loadLibrary вызова`() {
        val initBody = source.substringAfter("init {").substringBefore("@JvmStatic")
        val beginIdx = initBody.indexOf("loadLibrary begin")
        val callIdx = initBody.indexOf("System.loadLibrary")
        assertTrue(beginIdx in 0 until callIdx, "begin-лог должен быть до System.loadLibrary")
    }

    @Test
    fun `init использует PersistentLoggers для persistent диагностики`() {
        assertTrue(
            source.contains("PersistentLoggers.instance"),
            "init должен дублировать в PersistentLoggers — иначе load failure не попадёт в boot.log",
        )
    }

    @Test
    fun `init обрабатывает оба UnsatisfiedLinkError и SecurityException`() {
        val initBody = source.substringAfter("init {").substringBefore("@JvmStatic")
        assertTrue(initBody.contains("UnsatisfiedLinkError"), "должен ловить UnsatisfiedLinkError")
        assertTrue(initBody.contains("SecurityException"), "должен ловить SecurityException")
    }
}
