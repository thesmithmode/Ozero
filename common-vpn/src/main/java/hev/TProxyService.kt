package hev

/**
 * JNI-мост к upstream `heiher/hev-socks5-tunnel` (libhev-socks5-tunnel.so).
 *
 * Имена пакета и класса зафиксированы upstream-ом: hev-jni.c вызывает
 * `FindClass(env, "hev/TProxyService")` и привязывает методы через
 * `RegisterNatives` — поэтому класс ОБЯЗАН лежать в пакете `hev` и
 * называться `TProxyService`. Сигнатуры методов также строго upstream:
 * `TProxyStartService(String, int) : void` и `TProxyStopService() : void`.
 *
 * Имена не camelCase из-за upstream-контракта — supressed для ktlint/detekt.
 */
@Suppress("ktlint:standard:function-naming", "FunctionNaming")
object TProxyService {

    init {
        System.loadLibrary("hev-socks5-tunnel")
    }

    @JvmStatic
    external fun TProxyStartService(configPath: String, fd: Int)

    @JvmStatic
    external fun TProxyStopService()
}
