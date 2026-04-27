package ru.ozero.app.ui.permission

import android.os.Build

object BatteryOptimization {

        const val MIN_SDK = Build.VERSION_CODES.M

    const val PERMISSION = "android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS"

    fun isApplicable(sdkInt: Int): Boolean = sdkInt >= MIN_SDK

    fun resolve(sdkInt: Int, isIgnoring: Boolean, alreadyShown: Boolean): State =
        when {
            !isApplicable(sdkInt) -> State.NotApplicable
            isIgnoring -> State.AlreadyWhitelisted
            alreadyShown -> State.Skip
            else -> State.NeedsPrompt
        }

    sealed class State {
                data object NotApplicable : State()

                data object AlreadyWhitelisted : State()

                data object Skip : State()

                data object NeedsPrompt : State()
    }
}
