package ru.ozero.enginebyedpi.strategy

data class ByeDpiStrategy(
    val command: String,
)

object ByeDpiStrategiesParser {

    fun parse(content: String, sniValue: String = DEFAULT_SNI): List<ByeDpiStrategy> =
        content
            .replace("{sni}", "\"$sniValue\"")
            .lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .map { ByeDpiStrategy(command = it) }

    const val DEFAULT_SNI: String = "google.com"
    const val EXPECTED_COUNT: Int = 78
}
