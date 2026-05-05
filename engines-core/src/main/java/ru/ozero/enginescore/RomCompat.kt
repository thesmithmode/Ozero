package ru.ozero.enginescore

import android.os.Build

object RomCompat {
    fun isNubiaRedMagic(): Boolean {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val model = Build.MODEL.lowercase()
        return manufacturer.contains("nubia") ||
            manufacturer.contains("zte") ||
            model.contains("redmagic") ||
            model.contains("nx7")
    }
}
