package ru.ozero.enginemasterdns.deploy

class FakeSshTransport : SshTransport {
    var connectShouldFail = false
    var authShouldFail = false
    private val commandResponses = mutableMapOf<String, ArrayDeque<String>>()
    val executedCommands = mutableListOf<String>()

    fun setResponse(commandSubstring: String, response: String) {
        commandResponses[commandSubstring] = ArrayDeque(listOf(response))
    }

    fun setResponses(commandSubstring: String, responses: List<String>) {
        commandResponses[commandSubstring] = ArrayDeque(responses)
    }

    override fun connect(host: String, port: Int) {
        if (connectShouldFail) throw java.io.IOException("Connection refused to $host:$port")
    }

    override fun auth(login: String, password: CharArray) {
        if (authShouldFail) throw java.io.IOException("Auth failed")
    }

    override fun exec(command: String, timeoutMs: Long): String {
        executedCommands.add(command)
        val responses = commandResponses
            .entries
            .filter { command.contains(it.key) }
            .maxByOrNull { it.key.length }
            ?.value
            ?: return ""
        return if (responses.size > 1) responses.removeFirst() else responses.first()
    }

    var closeCalled = false

    override fun close() {
        closeCalled = true
    }
}
