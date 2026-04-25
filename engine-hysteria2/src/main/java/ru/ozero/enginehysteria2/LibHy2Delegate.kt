package ru.ozero.enginehysteria2

/**
 * Абстракция над gomobile-bind AAR (`hysteria2.aar`) для apernet/hysteria v2.
 * Существует для тестируемости: реальная реализация (LibHy2Jni) подгружает нативный
 * код через JNI; mock в тестах позволяет проверить Hy2Engine без AAR.
 *
 * Коды возврата следуют Unix-конвенции: 0 = OK, ненулевой = ошибка.
 */
interface LibHy2Delegate {
    fun startHy2(configJson: String): Int
    fun stopHy2(): Int
    fun version(): String
    fun queryStats(direction: String): Long
}
