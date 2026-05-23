package ru.ozero.enginemasterdns.deploy

import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.common.IOUtils
import net.schmizz.sshj.transport.kex.DHGexSHA1
import net.schmizz.sshj.transport.kex.DHGexSHA256
import net.schmizz.sshj.transport.kex.DHGroup14SHA1
import net.schmizz.sshj.transport.kex.DHGroup14SHA256
import net.schmizz.sshj.transport.kex.DHGroup1SHA1
import net.schmizz.sshj.transport.verification.PromiscuousVerifier
import ru.ozero.enginescore.PersistentLoggers
import java.util.concurrent.TimeUnit

internal class SshjTransport : SshTransport {

    private var client: SSHClient? = null

    override fun connect(host: String, port: Int) {
        val baseClient = createClient()
        try {
            baseClient.connect(host, port)
            client = baseClient
            return
        } catch (t: Throwable) {
            baseClient.disconnect()
            if (!isCurve25519ProviderError(t)) throw t
            PersistentLoggers.warn(TAG, "connect: X25519 provider unavailable; retrying with legacy KEX")
        }

        val fallbackClient = createClient().apply {
            transport.config.keyExchangeFactories = listOf(
                DHGroup14SHA256.Factory(),
                DHGroup14SHA1.Factory(),
                DHGexSHA256.Factory(),
                DHGexSHA1.Factory(),
                DHGroup1SHA1.Factory(),
            )
        }
        fallbackClient.connect(host, port)
        client = fallbackClient
    }

    override fun auth(login: String, password: CharArray) {
        val ssh = client ?: error("connect() must be called before auth()")
        ssh.authPassword(login, String(password))
    }

    override fun exec(command: String, timeoutMs: Long): String {
        val ssh = client ?: error("connect() must be called before exec()")
        val session = ssh.startSession()
        return try {
            val cmd = session.exec(command)
            val stdout = IOUtils.readFully(cmd.inputStream).toString()
            val stderr = runCatching { IOUtils.readFully(cmd.errorStream).toString() }.getOrDefault("")
            cmd.join(timeoutMs, TimeUnit.MILLISECONDS)
            val exit = cmd.exitStatus
            if (exit != null && exit != 0) {
                PersistentLoggers.warn(
                    TAG,
                    "exec[exit=$exit] cmd=${command.take(CMD_LOG_MAX)}" +
                        " stderr=${stderr.take(STDERR_LOG_MAX).trim()}",
                )
            }
            stdout
        } finally {
            session.close()
        }
    }

    override fun close() {
        client?.disconnect()
        client = null
    }

    private companion object {
        const val TAG = "SshjTransport"
        const val CONNECTION_TIMEOUT_MS = 15_000L
        const val CMD_LOG_MAX = 120
        const val STDERR_LOG_MAX = 400

        private fun isCurve25519ProviderError(t: Throwable): Boolean {
            var current: Throwable? = t
            while (current != null) {
                val message = current.message.orEmpty()
                if (message.contains("no such algorithm: X25519", ignoreCase = true)) {
                    return true
                }
                current = current.cause
            }
            return false
        }

        private fun createClient(): SSHClient = SSHClient().apply {
            addHostKeyVerifier(PromiscuousVerifier())
            connectTimeout = CONNECTION_TIMEOUT_MS.toInt()
        }
    }
}
