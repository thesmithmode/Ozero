package ru.ozero.enginemasterdns.deploy

import net.schmizz.sshj.AndroidConfig
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.common.IOUtils
import net.schmizz.sshj.transport.verification.PromiscuousVerifier
import org.bouncycastle.jce.provider.BouncyCastleProvider
import ru.ozero.enginescore.PersistentLoggers
import java.security.Security
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

internal class SshjTransport : SshTransport {

    private var client: SSHClient? = null

    override fun connect(host: String, port: Int) {
        ensureBouncyCastleProvider()
        val config = AndroidConfig()
        config.keyExchangeFactories = config.keyExchangeFactories.filter {
            val n = it.name.lowercase()
            "curve25519" !in n && "group-exchange" !in n
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
            val drainExecutor = Executors.newFixedThreadPool(2)
            try {
                val stdoutFuture = drainExecutor.submit(
                    Callable { IOUtils.readFully(cmd.inputStream).toString() },
                )
                val stderrFuture = drainExecutor.submit(
                    Callable {
                        runCatching { IOUtils.readFully(cmd.errorStream).toString() }.getOrDefault("")
                    },
                )
                cmd.join(timeoutMs, TimeUnit.MILLISECONDS)
                val exit = cmd.exitStatus
                if (exit == null) {
                    runCatching { cmd.close() }
                    PersistentLoggers.warn(TAG, "exec[timeout=${timeoutMs}ms] cmd=${command.take(CMD_LOG_MAX)}")
                    return "ERR_TIMEOUT|timeoutMs=$timeoutMs"
                }
                val stdout = stdoutFuture.awaitDrained()
                val stderr = stderrFuture.awaitDrained()
                if (exit != 0) {
                    PersistentLoggers.warn(
                        TAG,
                        "exec[exit=$exit] cmd=${command.take(CMD_LOG_MAX)}" +
                            " stderr=${stderr.take(STDERR_LOG_MAX).trim()}",
                    )
                }
                stdout
            } finally {
                drainExecutor.shutdownNow()
            }
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
        const val DRAIN_AWAIT_MS = 1_000L

        @Volatile
        private var bcRegistered = false

        @Synchronized
        private fun ensureBouncyCastleProvider() {
            if (bcRegistered) return
            Security.removeProvider("BC")
            Security.insertProviderAt(BouncyCastleProvider(), 1)
            bcRegistered = true
        }

        private fun Future<String>.awaitDrained(): String =
            runCatching { get(DRAIN_AWAIT_MS, TimeUnit.MILLISECONDS) }.getOrDefault("")
    }
}
