package ru.ozero.engineopera

enum class OperaRegion { EU, AS, AM }

enum class OperaServerSelection(val argument: String) {
    FIRST("first"),
    RANDOM("random"),
    FASTEST("fastest"),
}

data class OperaProxyConfig(
    val region: OperaRegion = OperaRegion.EU,
    val selection: OperaServerSelection = OperaServerSelection.FASTEST,
    internal val alternateBootstrap: Boolean = false,
)
