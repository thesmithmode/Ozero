package ru.ozero.commonvpn

object HevSocksTunnel {

    init {
        System.loadLibrary("hev-socks5-tunnel")
    }

    /**
     * Публичный API: принимает только типизированный HevTunnelConfig.
     * Сырой YAML в JNI не передаётся — inject-защита: структура генерируется toYaml()
     * из проверенных полей (fd, host, port валидированы на Kotlin-стороне).
     */
    fun start(config: HevTunnelConfig): Int = startNative(config.toYaml())

    fun stop(): Unit = stopNative()

    private external fun startNative(configYaml: String): Int

    private external fun stopNative()
}
