package ru.ozero.enginetelegram

sealed class TelegramProxyState {
    data object Idle : TelegramProxyState()
    data object Starting : TelegramProxyState()
    data class Running(val port: Int, val secret: String) : TelegramProxyState()
    data class Error(val message: String) : TelegramProxyState()
}
