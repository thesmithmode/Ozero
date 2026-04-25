package ru.ozero.enginexray

/**
 * Абстракция над gomobile-bind AAR (`libxray.aar`). Существует для тестируемости:
 * реальная реализация (LibXrayJni) подгружает нативный код через JNI, мок в тестах
 * позволяет проверить XrayEngine без AAR-артефакта.
 *
 * Коды возврата следуют Unix-конвенции: 0 = OK, ненулевой = ошибка.
 */
interface LibXrayDelegate {
    fun startXray(configJson: String): Int
    fun stopXray(): Int
    fun version(): String
    fun queryStats(tag: String, direction: String): Long
}
