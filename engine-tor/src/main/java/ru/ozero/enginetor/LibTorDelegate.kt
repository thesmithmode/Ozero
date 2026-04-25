package ru.ozero.enginetor

/**
 * Абстракция над запуском tor + PT subprocess.
 *
 * Реальная реализация (LibTorExec) разворачивает binary `tor` и PT-бинари (`obfs4proxy`,
 * `snowflake-client`, `conjure-client`) из `nativeLibraryDir` (после установки dynamic
 * feature module `:dynamic_tor` ~50 МБ через PlayCore SplitInstallManager).
 *
 * Возврат: 0 = OK, ненулевой — ошибка spawn или конфига.
 */
interface LibTorDelegate {
    fun startTor(torrc: String): Int
    fun stopTor(): Int
    fun isBootstrapped(): Boolean
    fun bootstrapPercent(): Int
    fun version(): String
}
