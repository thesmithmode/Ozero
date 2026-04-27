package ru.ozero.app.ui.permission

import android.os.Build

object NotificationPermission {

    const val MIN_SDK = Build.VERSION_CODES.TIRAMISU

    const val PERMISSION = "android.permission.POST_NOTIFICATIONS"

    fun isApplicable(sdkInt: Int): Boolean = sdkInt >= MIN_SDK

    fun resolve(sdkInt: Int, hasGrant: Boolean, asked: Boolean): State =
        when {
            !isApplicable(sdkInt) -> State.NotApplicable
            hasGrant -> State.Granted
            asked -> State.Denied
            else -> State.NeedsRequest
        }

    sealed class State {
        data object NotApplicable : State()

        data object Granted : State()

        data object Denied : State()

        data object NeedsRequest : State()
    }
}
