package ru.ozero.enginescore

import java.util.concurrent.atomic.AtomicReference

/**
 * Process-wide guard для Go-рантайма.
 *
 * Why: libam-go (WARP/AmneziaWG) и libgojni (URnetwork SDK) — два независимых Go-рантайма.
 * После запуска один из них оставляет process-wide state (signal handlers, TLS slots),
 * который ломает second JNI_OnLoad другого Go-рантайма → SIGABRT в gcWriteBarrier.
 *
 * Подтверждено: pid=16101 в логе 2026-05-09 — WARP первый запуск 7ms успех, после URnetwork
 * lifecycle (start+stop) повторный awgTurnOn → SIGABRT через 100ms. Гипотеза f7cdcdc
 * (handle leak) была недостаточна — краши продолжились после фикса.
 *
 * Решение: per-process один Go-рантайм. Если попытка second runtime — отказать.
 * ChainOrchestrator упадёт на fallback engine (ByeDPI) или stopVpn если auto pool пуст.
 *
 * Reset невозможен — флаг живёт до Process.killProcess или OS-kill.
 */
object GoRuntimeGuard {
    private val acquired = AtomicReference<Owner?>(null)

    fun acquire(owner: Owner): Result {
        if (acquired.compareAndSet(null, owner)) return Result.Granted
        val current = acquired.get()
        return if (current == owner) Result.Granted else Result.Conflict(current ?: owner)
    }

    fun current(): Owner? = acquired.get()

    enum class Owner { AMNEZIA_WG, URNETWORK }

    sealed class Result {
        data object Granted : Result()
        data class Conflict(val activeOwner: Owner) : Result()
    }
}
