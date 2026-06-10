package ru.ozero.enginemasterdns.deploy

internal class FakeSshTransport : SshTransport {
    var connectShouldFail = false
    var authShouldFail = false
    private val commandResponses = mutableMapOf<String, ArrayDeque<String>>()
    private val commandFailures = mutableMapOf<String, RuntimeException>()
    val executedCommands = mutableListOf<String>()

    fun setResponse(commandSubstring: String, response: String) {
        commandResponses[commandSubstring] = ArrayDeque(listOf(response))
    }

    fun setResponses(commandSubstring: String, responses: List<String>) {
        commandResponses[commandSubstring] = ArrayDeque(responses)
    }

    fun failOn(commandSubstring: String, failure: RuntimeException = IllegalStateException("boom")) {
        commandFailures[commandSubstring] = failure
    }

    override fun connect(host: String, port: Int) {
        if (connectShouldFail) throw java.io.IOException("Connection refused to $host:$port")
    }

    override fun auth(login: String, password: CharArray) {
        if (authShouldFail) throw java.io.IOException("Auth failed")
    }

    override fun exec(command: String, timeoutMs: Long): String {
        executedCommands.add(command)
        commandFailures
            .entries
            .filter { command.contains(it.key) }
            .maxByOrNull { it.key.length }
            ?.value
            ?.let { throw it }
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
