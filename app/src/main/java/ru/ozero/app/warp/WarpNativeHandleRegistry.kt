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
        if (remove(handle)) turnOff(handle)
    }

    fun releaseAll() {
        val activeHandles = drain()
        activeHandles.forEach(turnOff)
    }

    @Synchronized
    private fun remove(handle: Int): Boolean = handles.remove(handle)

    @Synchronized
    private fun drain(): List<Int> = handles.toList().also(handles::clear)
}
