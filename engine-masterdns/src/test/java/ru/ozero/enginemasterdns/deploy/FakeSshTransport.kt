package ru.ozero.enginemasterdns.deploy

class FakeSshTransport : SshTransport {
    var connectShouldFail = false
    var authShouldFail = false
    private val commandResponses = mutableMapOf<String, String>()
    val executedCommands = mutableListOf<String>()

    fun setResponse(commandSubstring: String, response: String) {
        commandResponses[commandSubstring] = response
    }

    override fun connect(host: String, port: Int) {
        if (connectShouldFail) throw java.io.IOException("Connection refused to $host:$port")
    }

    override fun auth(login: String, password: CharArray) {
        if (authShouldFail) throw java.io.IOException("Auth failed")
    }

    override fun exec(command: String, timeoutMs: Long): String {
        executedCommands.add(command)
        for ((key, value) in commandResponses) {
            if (command.contains(key)) return value
        }
        return ""
    }

    var closeCalled = false

    override fun close() {
        closeCalled = true
    }
}
