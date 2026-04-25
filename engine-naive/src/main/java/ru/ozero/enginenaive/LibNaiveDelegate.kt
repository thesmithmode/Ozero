package ru.ozero.enginenaive

/**
 * Абстракция над запуском naiveproxy-бинаря.
 *
 * NaiveProxy — нативный CLI-исполняемый файл (форк Chromium net stack), не Go-библиотека.
 * Реальная реализация (LibNaiveExec) разворачивает binary из APK assets в `nativeLibraryDir`,
 * пишет config в tmp-файл и запускает subprocess (`Runtime.exec`). Mock в тестах позволяет
 * проверить NaiveEngine без бинаря.
 *
 * Возврат: 0 = OK (процесс запущен), ненулевой — ошибка spawn.
 */
interface LibNaiveDelegate {
    fun startNaive(configJson: String): Int
    fun stopNaive(): Int
    fun isAlive(): Boolean
    fun version(): String
}
