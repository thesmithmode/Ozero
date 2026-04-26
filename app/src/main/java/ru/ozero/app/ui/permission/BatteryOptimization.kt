package ru.ozero.app.ui.permission

import android.os.Build

/**
 * RT.7.3: pure-Kotlin helper для опционального battery-whitelist prompt
 * (`ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`).
 *
 * Логика отделена от Android-API чтобы тестироваться на JVM без Robolectric:
 * фактическая проверка `PowerManager.isIgnoringBatteryOptimizations` и старт
 * Intent'а делается из Activity, а решение «показывать ли диалог» — здесь.
 *
 * Контракт: показываем строго один раз — после первого успешного включения VPN.
 * Если уже whitelist'ed или уже показывали — не показываем. Поведение не блокирует
 * подключение: VPN стартует независимо от выбора пользователя.
 */
object BatteryOptimization {

    /** API 23 (Marshmallow) — когда появился Doze и `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`. */
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
        /** API < 23: Doze отсутствует, prompt не нужен. */
        data object NotApplicable : State()

        /** Уже в whitelist'е (например, OEM-skin вытащил приложение). */
        data object AlreadyWhitelisted : State()

        /** Prompt показывали — повторно не дёргаем (UX). */
        data object Skip : State()

        /** Первое включение VPN — показываем системный диалог. */
        data object NeedsPrompt : State()
    }
}
