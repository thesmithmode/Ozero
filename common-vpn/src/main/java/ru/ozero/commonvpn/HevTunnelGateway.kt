package ru.ozero.commonvpn

/**
 * Шлюз к hev-socks5-tunnel JNI. Существует для тестируемости: реальный
 * [HevSocksTunnel] вызывает System.loadLibrary в init и не запускается на JVM.
 * Мок реализация возвращает заранее заданный код, не трогая нативку.
 *
 * Возврат: 0 = OK, ненулевой = ошибка (туннель не поднялся).
 */
interface HevTunnelGateway {
    fun start(config: HevTunnelConfig): Int
    fun stop()
}

/**
 * Production-реализация — делегирует в [HevSocksTunnel] (System.loadLibrary).
 * Не используется в unit-тестах: см. FakeHevTunnelGateway в test sources.
 */
object NativeHevTunnelGateway : HevTunnelGateway {
    override fun start(config: HevTunnelConfig): Int = HevSocksTunnel.start(config)
    override fun stop() {
        HevSocksTunnel.stop()
    }
}
