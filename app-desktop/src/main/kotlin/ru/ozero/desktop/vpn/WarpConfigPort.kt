package ru.ozero.desktop.vpn

import java.io.File

interface WarpConfigPort {
    fun loadWarpConfigText(): String?
    fun saveWarpConfigText(text: String)
}

class DefaultWarpConfigPort(
    private val file: File = File(System.getProperty("user.home"), ".ozero/warp.conf"),
) : WarpConfigPort {

    override fun loadWarpConfigText(): String? = if (file.exists()) file.readText() else null

    override fun saveWarpConfigText(text: String) {
        file.parentFile?.mkdirs()
        if (text.isBlank()) {
            file.delete()
            return
        }
        file.writeText(text)
    }
}
