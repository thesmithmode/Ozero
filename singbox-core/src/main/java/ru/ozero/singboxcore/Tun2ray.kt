package ru.ozero.singboxcore

class Tun2ray internal constructor(private val native: libcore.Tun2ray) {

    fun close() = native.close()

    companion object {
        fun create(config: TunConfig): Tun2ray = Tun2ray(libcore.Libcore.newTun2ray(config.native))
    }
}
