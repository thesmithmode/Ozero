package ru.ozero.enginemasterdns.deploy

import net.schmizz.sshj.AndroidConfig
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.common.IOUtils
import net.schmizz.sshj.transport.verification.PromiscuousVerifier
import ru.ozero.enginescore.PersistentLoggers
import java.util.concurrent.TimeUnit

internal class SshjTransport : SshTransport {

    private var client: SSHClient? = null

    override fun connect(host: String, port: Int) {
        val config = AndroidConfig()
        config.keyExchangeFactories = config.keyExchangeFactories.filter {
            "curve25519" !in it.name.lowercase()
        }
        val ssh = SSHClient(config)
        ssh.addHostKeyVerifier(PromiscuousVerifier())
        ssh.connectTimeout = CONNECTION_TIMEOUT_MS.toInt()
        ssh.connect(host, port)
        client = ssh
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
    }
}
