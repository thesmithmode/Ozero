package ru.ozero.coreorchestrator

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.ozero.coreapi.Engine
import ru.ozero.coreapi.ProbeResult

enum class HealthStatus { Healthy, Degraded }

class HealthMonitor(
    private val engine: Engine,
    private val scope: CoroutineScope,
    private val probeIntervalMs: Long = 30_000L,
    private val failThreshold: Int = 3,
) {
    private val _status = MutableStateFlow(HealthStatus.Healthy)
    val status: StateFlow<HealthStatus> = _status.asStateFlow()

    private var job: Job? = null
    private var consecutiveFails = 0

    fun start() {
        Log.i(TAG, "start interval=${probeIntervalMs}ms threshold=$failThreshold")
        job = scope.launch {
            while (true) {
                delay(probeIntervalMs)
                val result = engine.probe()
                if (result is ProbeResult.Failure) {
                    consecutiveFails++
                    Log.w(TAG, "probe failed $consecutiveFails/$failThreshold: ${result.reason}")
                    if (consecutiveFails >= failThreshold) {
                        Log.e(TAG, "порог достигнут → Degraded")
                        _status.value = HealthStatus.Degraded
                    }
                } else {
                    if (consecutiveFails > 0) Log.i(TAG, "recovered после $consecutiveFails фейлов")
                    consecutiveFails = 0
                    _status.value = HealthStatus.Healthy
                }
            }
        }
    }

    fun stop() {
        Log.i(TAG, "stop")
        job?.cancel()
        job = null
        consecutiveFails = 0
        _status.value = HealthStatus.Healthy
    }

    private companion object {
        const val TAG = "HealthMonitor"
    }
}
