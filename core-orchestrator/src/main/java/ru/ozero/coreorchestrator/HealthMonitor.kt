package ru.ozero.coreorchestrator

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
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

    private val lock = Any()

    @Volatile
    private var job: Job? = null
    private var consecutiveFails = 0

    fun start() {
        synchronized(lock) {
            if (job?.isActive == true) {
                Log.w(TAG, "start ignored — already running")
                return
            }
            Log.i(TAG, "start interval=${probeIntervalMs}ms threshold=$failThreshold")
            consecutiveFails = 0
            job = scope.launch {
                while (isActive) {
                    delay(probeIntervalMs)
                    val result = engine.probe()
                                                            if (!isActive) return@launch
                    if (result is ProbeResult.Failure) {
                        val fails = synchronized(lock) { ++consecutiveFails }
                        Log.w(TAG, "probe failed $fails/$failThreshold: ${result.reason}")
                        if (fails >= failThreshold) {
                            Log.e(TAG, "порог достигнут → Degraded")
                            _status.value = HealthStatus.Degraded
                        }
                    } else {
                        val prevFails = synchronized(lock) {
                            val prev = consecutiveFails
                            consecutiveFails = 0
                            prev
                        }
                        if (prevFails > 0) Log.i(TAG, "recovered после $prevFails фейлов")
                        _status.value = HealthStatus.Healthy
                    }
                }
            }
        }
    }

        suspend fun stop() {
        val toJoin = synchronized(lock) {
            Log.i(TAG, "stop")
            val current = job
            job = null
            consecutiveFails = 0
            _status.value = HealthStatus.Healthy
            current
        }
        toJoin?.cancelAndJoin()
    }

    private companion object {
        const val TAG = "HealthMonitor"
    }
}
