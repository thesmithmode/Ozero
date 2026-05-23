package ru.ozero.enginemasterdns.deploy

internal interface SshTransport {
    fun connect(host: String, port: Int)
    fun auth(login: String, password: CharArray)
    fun exec(command: String, timeoutMs: Long = 60_000L): String
    fun close()
}
