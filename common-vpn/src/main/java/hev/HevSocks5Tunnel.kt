package hev

/**
 * JNI-мост к upstream `heiher/hev-socks5-tunnel` (libhev-socks5-tunnel.so).
 *
 * Имена пакета и метода зафиксированы upstream'ом: JNI symbol resolution делается
 * по правилу `Java_<package>_<class>_<method>`, поэтому класс ОБЯЗАН лежать в
 * пакете `hev` и называться `HevSocks5Tunnel` — иначе symbols не подхватятся.
 *
 * Конфиг передаётся через путь к YAML-файлу + tun fd напрямую (не упаковывается
 * в YAML — upstream дублирует fd как параметр).
 */
object HevSocks5Tunnel {

    init {
        System.loadLibrary("hev-socks5-tunnel")
    }

    @JvmStatic
    external fun TProxyStartService(configPath: String, fd: Int): Int

    @JvmStatic
    external fun TProxyStopService()

    @JvmStatic
    @Suppress("unused")
    external fun TProxyGetStats(): LongArray
}
