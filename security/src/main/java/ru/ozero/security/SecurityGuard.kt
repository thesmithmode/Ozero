package ru.ozero.security

import android.util.Log
import ru.ozero.security.antidebug.AntiDebugCheck
import ru.ozero.security.antiemu.AntiEmulatorCheck
import ru.ozero.security.antifrida.AntiFridaCheck

/**
 * Композитор runtime-проверок.
 *
 * При запуске приложения вызвать `enforce()`; при первом тревожном сигнале — kill-switch:
 * - закрыть VPN tun (нельзя пускать трафик в инструментированном процессе)
 * - очистить in-memory ключи и подписки
 * - вернуть пустой UI
 */
class SecurityGuard(
    private val antiDebug: AntiDebugCheck = AntiDebugCheck(),
    private val antiFrida: AntiFridaCheck = AntiFridaCheck(),
    private val antiEmu: AntiEmulatorCheck = AntiEmulatorCheck(),
    private val isReleaseBuild: () -> Boolean = { !android.os.Build.TYPE.equals("eng") },
) {

    sealed class Verdict {
        data object Clean : Verdict()
        data class Compromised(val reasons: List<String>) : Verdict()
    }

    fun check(): Verdict {
        val reasons = mutableListOf<String>()
        if (antiDebug.isDebuggerAttached()) reasons += "debugger"
        if (antiFrida.isHookFrameworkPresent()) reasons += "hook-framework"
        if (isReleaseBuild() && antiEmu.isEmulator()) {
            reasons += antiEmu.matchedReasons().map { "emu:$it" }
        }
        return if (reasons.isEmpty()) Verdict.Clean else Verdict.Compromised(reasons)
    }

    fun enforceOrThrow() {
        when (val v = check()) {
            is Verdict.Clean -> Unit
            is Verdict.Compromised -> {
                // Причины пишем ТОЛЬКО в Log.e (видно разработчику), не в exception.message.
                // SecurityException.message попадает в crash-репорты (Crashlytics/Firebase) и
                // может сообщить атакующему какие именно проверки сработали.
                Log.e(TAG, "security: compromised env, причины=${v.reasons}")
                throw SecurityException("compromised")
            }
        }
    }

    private companion object {
        const val TAG = "SecurityGuard"
    }
}
