package ru.ozero.enginemasterdns.deploy

import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.common.IOUtils
import net.schmizz.sshj.transport.TransportException
import net.schmizz.sshj.transport.verification.PromiscuousVerifier
import ru.ozero.enginescore.PersistentLoggers
import java.util.concurrent.TimeUnit

internal class SshjTransport : SshTransport {

    private var client: SSHClient? = null

    override fun connect(host: String, port: Int) {
        client = connectClient(host, port)
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
        const val SSHJ_X25519_EXCEPTION_MARKER = "no such algorithm: X25519 for provider BC"
        const val KEX_FALLBACK = "diffie-hellman-group14-sha256,diffie-hellman-group14-sha1"
    }

    private fun connectClient(host: String, port: Int): SSHClient {
        return try {
            connectClient(host, port, null)
        } catch (e: TransportException) {
            val shouldRetryWithKexFallback = e.message?.contains(SSHJ_X25519_EXCEPTION_MARKER) == true
            if (!shouldRetryWithKexFallback) throw e
            PersistentLoggers.warn(TAG, "connect: X25519 unavailable in BC, retry with fallback KEX list")
            connectClient(host, port, KEX_FALLBACK)
        }
    }

    private fun connectClient(host: String, port: Int, kexOverride: String?): SSHClient {
        val ssh = SSHClient()
        ssh.addHostKeyVerifier(PromiscuousVerifier())
        ssh.connectTimeout = CONNECTION_TIMEOUT_MS.toInt()
        if (kexOverride != null) {
            ssh.transport.config.keyExchangeFactories = kexOverride
        }
        ssh.connect(host, port)
        return ssh
    }
}
