package ru.ozero.app.ui.onboarding

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

/**
 * RT.9.2: точка расширения для первичного bootstrap'а подписки на первом запуске.
 *
 * Сейчас — заглушка с логированием. Реальная реализация подключится когда:
 * - `SubscriptionManager` будет в DI app-модуля,
 * - в config'е появится `DEFAULT_SUBSCRIPTION_URL` + Ed25519 publicKey,
 * - E2.x закроет backend distribution (`https://sub.ozero.app/default.json.sig`).
 *
 * Контракт: метод не блокирует UX, ошибки только в Log. При отсутствии URL —
 * no-op. Серверы можно добавить позже через deeplink (RT.8) или ручной импорт.
 */
@Singleton
open class FirstRunBootstrap @Inject constructor() {

    open fun runIfFirstStart() {
        Log.i(TAG, "first-run bootstrap: skipped — backend URL не сконфигурирован (E2.x)")
        // TODO: после E2.x — SubscriptionManager.sync(DEFAULT_SUBSCRIPTION_URL).
    }

    private companion object {
        const val TAG = "FirstRunBootstrap"
    }
}
