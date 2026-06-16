package ru.ozero.enginescore

import kotlinx.coroutines.flow.Flow

interface EngineRuntimeConfigProvider {
    val engineId: EngineId
    val changes: Flow<Any?>
    val includeStarting: Boolean get() = true
    val replayAfterStarting: Boolean get() = false
    val adoptedBaselineFrom: Any? get() = null
    val restartReason: String
}
