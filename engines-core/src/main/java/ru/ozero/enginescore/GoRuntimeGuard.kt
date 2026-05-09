package ru.ozero.enginescore

import java.util.concurrent.atomic.AtomicReference

/**
 * Process-wide guard для Go-рантайма.
 *
 * Why: libam-go (WARP/AmneziaWG) и libgojni (URnetwork SDK) — два независимых Go-рантайма.
 * Concurrent JNI_OnLoad обоих ломал libgojni в gcWriteBarrier. CLAUDE.md инвариант:
 * оба .so грузятся eager-синхронно в OzeroApp.onCreate (main thread, до async-корутин),
 * после этого они coexist resident в процессе. Guard сериализует **active runtime**:
 * только один из движков использует свой Go в момент времени, второй ждёт release.
 *
 * release(owner) — обязательный hook в EngineWarp.detachTun / EngineUrnetwork.stop после
 * teardown SDK-state (awgTurnOff / Sdk.close + ioLoop.close + cv.disconnect). Без release
 * следующий движок другого owner получит Conflict навсегда (sticky owner).
 */
object GoRuntimeGuard {
    private val acquired = AtomicReference<Owner?>(null)

    fun acquire(owner: Owner): Result {
        if (acquired.compareAndSet(null, owner)) return Result.Granted
        val current = acquired.get()
        return if (current == owner) Result.Granted else Result.Conflict(current ?: owner)
    }

    fun release(owner: Owner) {
        acquired.compareAndSet(owner, null)
    }

    fun current(): Owner? = acquired.get()

    enum class Owner { AMNEZIA_WG, URNETWORK }

    sealed class Result {
        data object Granted : Result()
        data class Conflict(val activeOwner: Owner) : Result()
    }
}
