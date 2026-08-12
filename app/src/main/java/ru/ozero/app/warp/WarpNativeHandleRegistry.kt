package ru.ozero.app.warp

internal class WarpNativeHandleRegistry(
    private val turnOff: (Int) -> Unit,
) {
    private val handles = mutableSetOf<Int>()

    @Synchronized
    fun register(handle: Int) {
        if (handle >= 0) handles += handle
    }

    fun release(handle: Int) {
        if (!contains(handle)) return
        turnOff(handle)
        remove(handle)
    }

    fun releaseAll(): Boolean {
        snapshot().forEach { handle ->
            runCatching { turnOff(handle) }
                .onSuccess { remove(handle) }
        }
        return isEmpty()
    }

    @Synchronized
    fun isEmpty(): Boolean = handles.isEmpty()

    @Synchronized
    private fun contains(handle: Int): Boolean = handle in handles

    @Synchronized
    private fun remove(handle: Int): Boolean = handles.remove(handle)

    @Synchronized
    private fun snapshot(): List<Int> = handles.toList()
}
