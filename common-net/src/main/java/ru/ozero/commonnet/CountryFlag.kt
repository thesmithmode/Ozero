package ru.ozero.commonnet

object CountryFlag {

    private const val WHITE_FLAG = "🏳"
    private const val A_OFFSET = 0x1F1E6 - 'A'.code

    fun emoji(countryCode: String?): String {
        val code = countryCode?.trim()?.uppercase() ?: return WHITE_FLAG
        if (code.length != ISO2_LENGTH) return WHITE_FLAG
        if (!code.all { it in 'A'..'Z' }) return WHITE_FLAG
        val first = code[0].code + A_OFFSET
        val second = code[1].code + A_OFFSET
        return String(intArrayOf(first, second), 0, 2)
    }

    private const val ISO2_LENGTH = 2
}
