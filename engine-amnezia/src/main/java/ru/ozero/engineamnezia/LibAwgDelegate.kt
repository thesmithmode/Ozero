package ru.ozero.engineamnezia

/**
 * Абстракция над gomobile-bind AAR (`amneziawg.aar`) над форком wireguard-go от amnezia.
 * Реальная реализация (LibAwgJni) подгружает нативный код через JNI; mock в тестах
 * позволяет проверить AwgEngine без AAR.
 *
 * Возврат: 0 = OK, ненулевое = ошибка native-стороны.
 */
interface LibAwgDelegate {
    fun startAwg(configIni: String): Int
    fun stopAwg(): Int
    fun isUp(): Boolean
    fun version(): String
    fun queryStats(direction: String): Long
}
