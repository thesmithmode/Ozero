package ru.ozero.app.ui.permission

import android.os.Build

/**
 * Pure-Kotlin helper для разрешения POST_NOTIFICATIONS (Android 13+).
 *
 * Compose-обёртка ([NotificationPermissionGuard]) использует [resolve] для
 * принятия решения на основе API-уровня и текущего grant. Изоляция от Android-API
 * нужна для unit-тестов: реальный ContextCompat.checkSelfPermission требует
 * Robolectric/instrumentation, а pure-функция тестируется на JVM.
 */
object NotificationPermission {

    /** Версия Android начиная с которой POST_NOTIFICATIONS обязателен (Tiramisu = API 33). */
    const val MIN_SDK = Build.VERSION_CODES.TIRAMISU

    /** Manifest-имя permission — выделено для тестов (избегаем магической строки). */
    const val PERMISSION = "android.permission.POST_NOTIFICATIONS"

    /** На устройствах до API 33 permission не существует — notification рисуется без него. */
    fun isApplicable(sdkInt: Int): Boolean = sdkInt >= MIN_SDK

    fun resolve(sdkInt: Int, hasGrant: Boolean, asked: Boolean): State =
        when {
            !isApplicable(sdkInt) -> State.NotApplicable
            hasGrant -> State.Granted
            asked -> State.Denied
            else -> State.NeedsRequest
        }

    sealed class State {
        /** API < 33: permission не нужен, notification работает out-of-box. */
        data object NotApplicable : State()

        /** Пользователь дал разрешение. */
        data object Granted : State()

        /**
         * Пользователь отклонил. На API 33 после второго отказа диалог больше не
         * показывается — нужно вести в Settings (UI задача в RT.4 продолжении).
         */
        data object Denied : State()

        /** Первый запуск или config change без ответа — нужно показать system-dialog. */
        data object NeedsRequest : State()
    }
}
