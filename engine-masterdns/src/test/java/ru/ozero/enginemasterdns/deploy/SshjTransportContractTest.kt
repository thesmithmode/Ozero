package ru.ozero.enginemasterdns.deploy

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertTrue

class SshjTransportContractTest {

    private val source: String by lazy {
        val moduleRoot = File(System.getProperty("user.dir") ?: ".")
        File(moduleRoot, "src/main/java/ru/ozero/enginemasterdns/deploy/SshjTransport.kt").readText()
    }

    @Test
    fun `exec captures errorStream for stderr diagnostics`() {
        assertTrue(
            source.contains("cmd.errorStream"),
            "exec must read cmd.errorStream — без stderr silent fail без диагностики",
        )
    }

    @Test
    fun `exec checks cmd exitStatus`() {
        assertTrue(
            source.contains("cmd.exitStatus"),
            "exec must check cmd.exitStatus — non-zero exit без логов = недиагностируемый fail",
        )
    }

    @Test
    fun `non-zero exit logs stderr via PersistentLoggers warn`() {
        val execBlock = source.substringAfter("override fun exec")
            .substringBefore("override fun close")
        assertTrue(
            execBlock.contains("PersistentLoggers.warn"),
            "non-zero exit must produce PersistentLoggers.warn для post-mortem диагностики deploy fail",
        )
        assertTrue(
            execBlock.contains("exit=") && execBlock.contains("stderr="),
            "warn log must include exit code и stderr — иначе непонятно почему упало",
        )
    }
}
