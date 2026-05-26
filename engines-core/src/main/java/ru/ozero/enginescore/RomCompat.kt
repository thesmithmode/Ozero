package ru.ozero.enginescore

object RomCompat {
    fun isNubiaRedMagic(
        manufacturer: String = "",
        model: String = "",
    ): Boolean {
        val mfr = manufacturer.lowercase()
        val mdl = model.lowercase()
        return mfr.contains("nubia") ||
            mfr.contains("zte") ||
            mdl.contains("redmagic") ||
            mdl.contains("nx7")
    }
}
