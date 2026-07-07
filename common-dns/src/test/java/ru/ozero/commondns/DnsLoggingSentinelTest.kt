package ru.ozero.commondns

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DnsLoggingSentinelTest {

    private fun srcFile(rel: String): String {
        val moduleRoot = File(System.getProperty("user.dir") ?: ".")
        val f = File(moduleRoot, rel)
        assertTrue(f.exists(), "$rel не найден: $f")
        return f.readText()
    }

    @Test
    fun `DohResolver — DNS failures через PersistentLoggers warn, не Log w`() {
        val src = srcFile("src/main/java/ru/ozero/commondns/DohResolver.kt")
        val executeBody = src.substringAfter("private suspend fun execute(").substringBefore("companion object")
        assertFalse(
            executeBody.contains("Log.w("),
            "Log.w в DohResolver.execute не попадает в boot.log → невозможно диагностировать DNS fail. " +
                "Использовать PersistentLoggers.warn",
        )
        assertTrue(
            executeBody.contains("PersistentLoggers.warn("),
            "DoH HTTP-fail/IO-fail обязан логироваться через PersistentLoggers.warn — " +
                "DNS-сбои критичны для диагностики offline/connect issues",
        )
    }

    @Test
    fun `DnsResolverChain — resolver failures через PersistentLoggers warn`() {
        val src = srcFile("src/main/java/ru/ozero/commondns/DnsResolver.kt")
        val resolveBody = src
            .substringAfter("override suspend fun resolve(hostname: String): DohResult")
            .substringBefore("private companion object")
        assertFalse(
            resolveBody.contains("Log.w("),
            "Log.w в DnsResolverChain.resolve не попадает в boot.log",
        )
        assertTrue(
            resolveBody.contains("PersistentLoggers.warn("),
            "chain resolver fall-through fail обязан логироваться через PersistentLoggers.warn",
        )
    }

    @Test
    fun `DnsResolverChain — persistent logs do not include raw hostname`() {
        val src = srcFile("src/main/java/ru/ozero/commondns/DnsResolver.kt")
        val resolveBody = src
            .substringAfter("override suspend fun resolve(hostname: String): DohResult")
            .substringBefore("private companion object")
        val persistentLines = resolveBody
            .lineSequence()
            .filter { it.contains("PersistentLoggers.") }
            .joinToString("\n")
        assertFalse(
            persistentLines.contains("$" + "hostname"),
            "persistent DNS diagnostics must not include raw query hostnames",
        )
    }
}
