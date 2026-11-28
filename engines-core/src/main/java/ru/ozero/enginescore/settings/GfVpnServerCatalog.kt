package ru.ozero.enginescore.settings

/** Server selection catalog from the reference 1.0.6 APK asset, not a live availability claim. */
object GfVpnServerCatalog {
    val countries: Map<String, List<String>> = mapOf(
        "BR" to listOf("BR1"),
        "CH" to listOf("CH1", "CH2", "CH3"),
        "DE" to listOf("DE7"),
        "DK" to listOf("DK2"),
        "EE" to listOf("EE1", "EE2"),
        "FI" to listOf("FI1", "FI2", "FI3"),
        "FR" to listOf("FR4", "FR6"),
        "GB" to listOf("GB1"),
        "HK0" to listOf("HK03", "HK04", "HK05"),
        "IN" to listOf("IN1"),
        "JP" to listOf("JP14", "JP15", "JP17", "JP7"),
        "LU" to listOf("LU1"),
        "NL" to listOf("NL3", "NL4", "NL5", "NL6", "NL7", "NL8"),
        "PL" to listOf("PL1"),
        "RO" to listOf("RO1"),
        "RU" to listOf("RU1"),
        "RU0" to listOf(
            "RU010", "RU011", "RU012", "RU013", "RU015", "RU016", "RU017",
            "RU018", "RU019", "RU02", "RU020", "RU03", "RU05",
        ),
        "SG" to listOf("SG10", "SG11", "SG12", "SG5", "SG6", "SG8", "SG9"),
        "SG0" to listOf("SG01", "SG02", "SG03"),
        "US" to listOf("US3", "US4"),
    )

    fun countryFor(selection: String): String? =
        countries.entries.firstOrNull { (country, groups) -> selection == country || selection in groups }?.key
}
