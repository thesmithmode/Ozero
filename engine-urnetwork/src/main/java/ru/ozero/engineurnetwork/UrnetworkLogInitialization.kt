package ru.ozero.engineurnetwork

import java.util.concurrent.atomic.AtomicBoolean

/** Native logging is process-wide; releasing a network space does not reset its sinks. */
internal class UrnetworkLogInitialization {
    private val attempted = AtomicBoolean(false)

    fun runOnce(initialize: () -> Unit) {
        // Even a throwing call may have already changed the native logger.
        if (attempted.compareAndSet(false, true)) initialize()
    }
}
