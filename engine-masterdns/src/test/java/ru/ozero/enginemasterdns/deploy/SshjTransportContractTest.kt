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
    fun `exec applies timeout before blocking stdout read`() {
        val execBlock = source.substringAfter("override fun exec")
            .substringBefore("override fun close")
        assertTrue(
            execBlock.indexOf("cmd.join(timeoutMs") < execBlock.indexOf("IOUtils.readFully(cmd.inputStream)"),
            "exec must join with timeout before readFully(inputStream), otherwise hung remote command bypasses timeout",
        )
        assertTrue(execBlock.contains("exit == null"))
        assertTrue(execBlock.contains("cmd.close()"))
        assertTrue(execBlock.contains("ERR_TIMEOUT|timeoutMs="))
    }

    @Test
    fun `curve25519 kex excluded to avoid X25519 NoSuchAlgorithmException on Android BC`() {
        assertTrue(
            source.contains("curve25519") && source.contains("filter"),
            "SshjTransport must filter out curve25519 from keyExchangeFactories — " +
                "Android BC lacks X25519 causing NoSuchAlgorithmException on kex handshake",
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
