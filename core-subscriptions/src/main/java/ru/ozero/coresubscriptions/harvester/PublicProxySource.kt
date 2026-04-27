package ru.ozero.coresubscriptions.harvester

data class PublicProxySource(
    val id: String,
    val url: String,
    val format: SourceFormat,
    val region: Region = Region.GLOBAL,
    val priority: Int = 5,
)

enum class SourceFormat {
        LINES,

        BASE64_LINES,

        JSON_ARRAY,
}

enum class Region {
        RU,

        GLOBAL,
}
