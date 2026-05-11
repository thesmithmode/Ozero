package ru.ozero.commonvpn

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.ozero.enginescore.EngineId
import ru.ozero.enginescore.PersistentLoggers

class TunnelController(
    private val stagnationMonitor: StatsStagnationMonitor = StatsStagnationMonitor(),
) {

    private val _state = MutableStateFlow<TunnelState>(TunnelState.Idle)
    val state: StateFlow<TunnelState> = _state.asStateFlow()
    private val _stats = MutableStateFlow<TunnelStats?>(null)
    val stats: StateFlow<TunnelStats?> = _stats.asStateFlow()
    private val _stagnant = MutableStateFlow(false)
    val stagnant: StateFlow<Boolean> = _stagnant.asStateFlow()
    private val _killswitchActive = MutableStateFlow(false)
    val killswitchActive: StateFlow<Boolean> = _killswitchActive.asStateFlow()
    private val _switching = MutableStateFlow<SwitchingTransition?>(null)
    val switching: StateFlow<SwitchingTransition?> = _switching.asStateFlow()
    private val lock = Any()
    private var sessionStartMs: Long = 0L
    private var prevTxBytes: Long = 0L
    private var prevRxBytes: Long = 0L
    private var prevTimestampMs: Long = 0L
    private var smoothedBpsIn: Double = 0.0
    private var smoothedBpsOut: Double = 0.0

    fun onProbing(engineId: EngineId? = null) = transition(TunnelState.Probing(engineId))

    fun onConnecting(engineId: EngineId) = transition(TunnelState.Connecting(engineId))

    fun onEngineStarted(engineId: EngineId, socksPort: Int) {
        sessionStartMs = System.currentTimeMillis()
        prevTxBytes = 0L
        prevRxBytes = 0L
        prevTimestampMs = 0L
        smoothedBpsIn = 0.0
        smoothedBpsOut = 0.0
        stagnationMonitor.reset()
        _stagnant.value = false
        transition(TunnelState.Connected(engineId, socksPort))
    }

    fun onEngineDied(engineId: EngineId, reason: String) =
        transition(TunnelState.Failed(engineId, reason), markError = true)

    fun onKillswitchEngaged(engineId: EngineId, reason: String) {
        transition(TunnelState.Failed(engineId, reason), markError = true)
        _killswitchActive.value = true
        PersistentLoggers.warn(TAG, "killswitch engaged: engine=$engineId reason=$reason")
    }

    fun onKillswitchReleased() {
        if (_killswitchActive.compareAndSet(true, false)) {
            PersistentLoggers.info(TAG, "killswitch released")
        }
    }

    fun onDisconnecting() = transition(TunnelState.Disconnecting)

    fun onSwitchingStarted(from: EngineId?, to: EngineId?) {
        _switching.value = SwitchingTransition(from, to)
        PersistentLoggers.info(TAG, "switching started: $from → $to")
    }

    fun onSwitchingFinished(reason: String) {
        if (_switching.compareAndSet(_switching.value, null)) {
            PersistentLoggers.info(TAG, "switching finished: $reason")
        }
    }

    fun reset() {
        _stats.value = null
        _stagnant.value = false
        _killswitchActive.value = false
        sessionStartMs = 0L
        prevTxBytes = 0L
        prevRxBytes = 0L
        prevTimestampMs = 0L
        smoothedBpsIn = 0.0
        smoothedBpsOut = 0.0
        stagnationMonitor.reset()
        transition(TunnelState.Idle)
    }

    fun updateStats(raw: TunnelStats) {
        synchronized(lock) {
            val dtMs = (raw.timestampMs - prevTimestampMs).coerceAtLeast(1L)
            val rawBpsIn: Double = if (prevTimestampMs > 0L) {
                ((raw.rxBytes - prevRxBytes).coerceAtLeast(0L) * 1000.0) / dtMs
            } else {
                0.0
            }
            val rawBpsOut: Double = if (prevTimestampMs > 0L) {
                ((raw.txBytes - prevTxBytes).coerceAtLeast(0L) * 1000.0) / dtMs
            } else {
                0.0
            }
            smoothedBpsIn = EWMA_ALPHA * rawBpsIn + (1.0 - EWMA_ALPHA) * smoothedBpsIn
            smoothedBpsOut = EWMA_ALPHA * rawBpsOut + (1.0 - EWMA_ALPHA) * smoothedBpsOut
            prevTxBytes = raw.txBytes
            prevRxBytes = raw.rxBytes
            prevTimestampMs = raw.timestampMs
            _stats.value = raw.copy(
                bpsIn = smoothedBpsIn,
                bpsOut = smoothedBpsOut,
                sessionStartMs = sessionStartMs,
            )
            val isConnected = _state.value is TunnelState.Connected
            val newlyStagnant = isConnected && stagnationMonitor.observe(raw.txBytes, raw.rxBytes)
            if (newlyStagnant != _stagnant.value) {
                _stagnant.value = newlyStagnant
                if (newlyStagnant) {
                    PersistentLoggers.warn(TAG, "stagnation detected: tx/rx flat for >= threshold")
                }
            }
        }
    }

    private fun transition(target: TunnelState, markError: Boolean = false) {
        synchronized(lock) {
            val current = _state.value
            if (current == target) return
            if (!isAllowed(current, target)) {
                PersistentLoggers.warn(
                    TAG,
                    "invalid transition ${name(current)} → ${name(target)} ignored",
                )
                return
            }
            if (markError) {
                PersistentLoggers.error(TAG, "${name(current)} → ${name(target)}")
            } else {
                PersistentLoggers.info(TAG, "${name(current)} → ${name(target)}")
            }
            _state.value = target
            val sw = _switching.value
            if (sw != null) {
                val terminal = when (target) {
                    is TunnelState.Connected -> true
                    is TunnelState.Failed -> true
                    else -> false
                }
                if (terminal) {
                    _switching.value = null
                    PersistentLoggers.info(TAG, "switching cleared on transition → ${name(target)}")
                }
            }
        }
    }

    private fun isAllowed(from: TunnelState, to: TunnelState): Boolean = when (from) {
        is TunnelState.Idle -> to is TunnelState.Probing
        is TunnelState.Probing ->
            to is TunnelState.Connecting ||
                to is TunnelState.Failed ||
                to is TunnelState.Disconnecting
        is TunnelState.Connecting ->
            to is TunnelState.Connected ||
                to is TunnelState.Failed ||
                to is TunnelState.Disconnecting
        is TunnelState.Connected ->
            to is TunnelState.Disconnecting ||
                to is TunnelState.Failed
        is TunnelState.Disconnecting ->
            to is TunnelState.Idle ||
                to is TunnelState.Failed
        is TunnelState.Failed ->
            to is TunnelState.Probing ||
                to is TunnelState.Idle ||
                to is TunnelState.Disconnecting
    }

    private fun name(s: TunnelState): String = when (s) {
        is TunnelState.Idle -> "Idle"
        is TunnelState.Probing -> "Probing"
        is TunnelState.Connecting -> "Connecting(${s.engineId})"
        is TunnelState.Connected -> "Connected(${s.engineId}, port=${s.socksPort})"
        is TunnelState.Failed -> "Failed(${s.engineId}, ${s.reason})"
        is TunnelState.Disconnecting -> "Disconnecting"
    }

    private companion object {
        const val TAG = "TunnelController"
        const val EWMA_ALPHA = 0.4
    }
}
