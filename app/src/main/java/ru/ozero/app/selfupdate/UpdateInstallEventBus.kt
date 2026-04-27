package ru.ozero.app.selfupdate

import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

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

    fun reset() {
        _events.resetReplayCache()
    }
}
