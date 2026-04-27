package ru.ozero.commonvpn

import android.content.Context
import android.util.Log
import java.io.File

/**
 * Шлюз к hev-socks5-tunnel JNI. Существует для тестируемости: реальный
 * [NativeHevTunnelGateway] вызывает System.loadLibrary в init и не запускается на JVM.
 * Мок реализация возвращает заранее заданный код, не трогая нативку.
 *
 * Возврат: 0 = OK, ненулевой = ошибка (туннель не поднялся).
 */
interface HevTunnelGateway {
    fun start(config: HevTunnelConfig): Int
    fun stop()
}

/**
 * Production-реализация. Пишет YAML-конфиг во временный файл (upstream API
 * принимает path) и делегирует в [hev.HevSocks5Tunnel] (System.loadLibrary).
 *
 * Upstream JNI symbol-naming требует точного `Java_hev_HevSocks5Tunnel_*`,
 * поэтому JNI-класс лежит в пакете `hev`.
 *
 * Конструктор принимает [cacheDir] и нативные функции через лямбды чтобы класс
 * был тестируем без эмулятора (Context.cacheDir подставляется фабрикой ниже).
 */
class NativeHevTunnelGateway(
    private val cacheDir: File,
    private val nativeStart: (configPath: String, fd: Int) -> Int = { path, fd ->
        hev.HevSocks5Tunnel.TProxyStartService(path, fd)
    },
    private val nativeStop: () -> Unit = { hev.HevSocks5Tunnel.TProxyStopService() },
) : HevTunnelGateway {

    constructor(context: Context) : this(cacheDir = context.cacheDir)

    override fun start(config: HevTunnelConfig): Int {
        val configFile = writeConfig(config)
        Log.i(TAG, "TProxyStartService path=${configFile.absolutePath} fd=${config.tunFd}")
        return runCatching { nativeStart(configFile.absolutePath, config.tunFd) }
            .onFailure { Log.e(TAG, "TProxyStartService threw", it) }
            .getOrElse { -1 }
    }

    override fun stop() {
        runCatching { nativeStop() }
            .onFailure { Log.w(TAG, "TProxyStopService threw", it) }
    }

    private fun writeConfig(config: HevTunnelConfig): File {
        // cacheDir безопасен — private app storage, не выживает между запусками,
        // что нам и нужно (fd валиден только в рамках одной VPN-сессии).
        if (!cacheDir.exists()) cacheDir.mkdirs()
        val file = File(cacheDir, CONFIG_FILE)
        file.writeText(config.toYaml())
        return file
    }

    private companion object {
        const val TAG = "NativeHevTunnel"
        const val CONFIG_FILE = "hev-socks5-tunnel.yaml"
    }
}
