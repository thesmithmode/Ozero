package ru.ozero.coresubscriptions.harvester

/**
 * E16.1: дескриптор public-репо с прокси-конфигами.
 *
 * `url` — raw GitHub URL (или любой HTTPS, отдающий plain-text).
 * `format` — как парсить ответ (см. [SourceFormat]).
 * `region` — RU-specific source даёт лучшие шансы на живой прокси
 * именно в РФ (фильтрация на стороне репо).
 *
 * Список источников хранится в `app/src/main/assets/proxy-sources.json`,
 * читается в DI через `SourceRegistry`. Изменение списка =
 * перевыпуск APK, но приоритеты внутри (live filter, RU-prefer)
 * управляются runtime'ом.
 */
data class PublicProxySource(
    val id: String,
    val url: String,
    val format: SourceFormat,
    val region: Region = Region.GLOBAL,
    val priority: Int = 5,
)

enum class SourceFormat {
    /** Один URI на строку (vless://, hy2://, ss://, ...). */
    LINES,

    /** Base64-decoded блоб → строки URI. Стандарт V2RayN/V2RayNG subscription. */
    BASE64_LINES,

    /** JSON-массив `{ "servers": ["uri", ...] }`. */
    JSON_ARRAY,
}

enum class Region {
    /** РФ-проверенные конфиги. Приоритет в выборке. */
    RU,

    /** Глобальные агрегаторы. Backup когда RU дают <10 живых. */
    GLOBAL,
}
