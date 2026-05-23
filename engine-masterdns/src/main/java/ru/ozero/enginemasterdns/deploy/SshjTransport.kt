package ru.ozero.enginemasterdns.deploy

import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.common.IOUtils
import net.schmizz.sshj.transport.verification.PromiscuousVerifier
import java.util.concurrent.TimeUnit

internal class SshjTransport : SshTransport {

    private var client: SSHClient? = null

    override fun connect(host: String, port: Int) {
        val ssh = SSHClient()
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
            val output = IOUtils.readFully(cmd.inputStream).toString()
            cmd.join(timeoutMs, TimeUnit.MILLISECONDS)
            output
        } finally {
            session.close()
        }
    }

    override fun close() {
        client?.disconnect()
        client = null
    }

    private companion object {
        const val CONNECTION_TIMEOUT_MS = 15_000L
    }
}
