package ru.ozero.app.selfupdate

import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Process-scope событийная шина self-update flow.
 *
 * Receiver инстанцируется системой по manifest-декларации, поэтому Hilt-инъекция
 * нетривиальна. Шина — простой singleton с replay=1 (последний event переживёт
 * recreate Activity на rotation/process-death-restore).
 *
 * extraBufferCapacity=8 — на случай быстрых эмитов (tryEmit не дропнет события
 * пока буфер не заполнится).
 */
object UpdateInstallEventBus {
    private const val TAG = "UpdateInstallBus"

    private val _events = MutableSharedFlow<UpdateInstallEvent>(
        replay = 1,
        extraBufferCapacity = 8,
    )

    val events: SharedFlow<UpdateInstallEvent> = _events.asSharedFlow()

    fun emit(event: UpdateInstallEvent) {
        val ok = _events.tryEmit(event)
        if (!ok) {
            Log.w(TAG, "buffer overflow, dropping $event")
        }
    }

    /**
     * Сбрасывает replay cache. Только для тестов — между кейсами в JUnit.
     */
    fun reset() {
        _events.resetReplayCache()
    }
}
