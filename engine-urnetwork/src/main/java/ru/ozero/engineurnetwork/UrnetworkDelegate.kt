package ru.ozero.engineurnetwork

/**
 * Абстракция над нативным URnetwork SDK (gomobile AAR).
 *
 * Интерфейс изолирует engine от прямой зависимости на com.bringyour классы,
 * что позволяет unit-тестировать движок без реального AAR.
 *
 * Реализации:
 * - [UrnetworkSdkDelegate] — prod: оборачивает com.bringyour.ConnectViewController
 * - [StubUrnetworkDelegate] — тесты: фиктивная реализация
 *
 * Состояния lifecycle: STOPPED → CONNECTING → CONNECTED → STOPPED
 */
interface UrnetworkDelegate {
    /**
     * Инициализирует SDK и начинает подключение.
     * @param jwtToken JWT токен авторизации
     * @param apiUrl   URL API urnetwork
     * @param region   Предпочитаемый регион или null
     * @param mode     CONSUMER/PROVIDER
     * @return true если инициализация успешна
     */
    fun connect(jwtToken: String, apiUrl: String, region: String?, mode: UrnetworkMode): Boolean

    /** Разрывает соединение и освобождает ресурсы */
    fun disconnect()

    /** Текущий статус соединения */
    fun connectionStatus(): UrnetworkConnectionStatus

    /** Версия SDK */
    fun sdkVersion(): String
}

enum class UrnetworkConnectionStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    FAILED,
}

enum class UrnetworkMode {
    /** Потребитель: использует P2P сеть для анонимного трафика */
    CONSUMER,

    /** Провайдер: предоставляет свой трафик другим участникам сети */
    PROVIDER,
}
