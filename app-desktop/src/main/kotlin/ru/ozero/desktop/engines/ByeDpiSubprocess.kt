package ru.ozero.desktop.engines

class ByeDpiSubprocess : SubprocessEngine("byedpi") {

    override fun buildArgs(socksPort: Int): List<String> =
        listOf("--port", socksPort.toString())

    fun buildArgsFromRawCmd(socksPort: Int, rawArgs: String): List<String> {
        val base = listOf("--port", socksPort.toString())
        val extra = rawArgs.trim().split("\\s+".toRegex()).filter { it.isNotEmpty() }
        return base + extra
    }
}
