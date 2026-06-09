package ru.ozero.enginescore

import android.os.Build

object RomCompat {
    fun isNubiaRedMagic(
        manufacturer: String = Build.MANUFACTURER.orEmpty(),
        model: String = Build.MODEL.orEmpty(),
    ): Boolean {
        val mfr = manufacturer.lowercase()
        val mdl = model.lowercase()
        return mfr.contains("nubia") ||
            mfr.contains("zte") ||
            mdl.contains("redmagic") ||
            mdl.contains("nx7")
    }
}
