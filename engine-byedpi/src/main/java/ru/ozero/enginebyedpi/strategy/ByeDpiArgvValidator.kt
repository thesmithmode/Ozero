package ru.ozero.enginebyedpi.strategy

object ByeDpiArgvValidator {
    private val longValueOptions = mapOf(
        "--fake" to ::isSignedInt,
        "--ttl" to ::isUnsignedInt,
        "--split" to ::isModifierValue,
        "--disorder" to ::isModifierValue,
    )

    fun isValid(command: String): Boolean {
        val tokens = command.trim().split(Regex("\\s+")).filter(String::isNotBlank)
        if (tokens.isEmpty()) return false
        var expectedValueFor: String? = null
        for (token in tokens) {
            if (token.contains("{") || token.contains("}")) return false
            val pending = expectedValueFor
            if (pending != null) {
                if (!isValueValid(pending, token)) return false
                expectedValueFor = null
                continue
            }
            when {
                token == "-n" -> expectedValueFor = token
                token.startsWith("--") -> {
                    if (token !in longValueOptions) return false
                    expectedValueFor = token
                }
                token.startsWith("-") -> {
                    if (!isFlagToken(token)) return false
                }
                else -> return false
            }
        }
        return expectedValueFor == null
    }

    private fun isValueValid(option: String, token: String): Boolean =
        when (option) {
            "-n" -> isDomainValue(token)
            else -> longValueOptions[option]?.invoke(token) == true
        }

    private fun isFlagToken(token: String): Boolean {
        if (token.length < 2) return false
        if (token.any(Char::isWhitespace)) return false
        if (token == "-K" || token == "-S" || token == "-Y") return true
        val option = token[1]
        return when (option) {
            'A' -> token.length > 2 && token.drop(2).all { it in "ntrs,c" }
            'K' -> token.length > 2 && token.drop(2).all { it in "tuh," }
            'Q' -> token == "-Qr"
            'M' -> token.length > 2 && token.drop(2).all { it in "hdr," }
            'l' -> token.startsWith("-l:")
            'a', 'd', 'e', 'f', 'm', 'o', 'p', 'q', 'r', 's', 't', 'O', 'R' ->
                token.length > 2 && token.drop(2).all(::isAttachedValueChar)
            else -> false
        }
    }

    private fun isAttachedValueChar(ch: Char): Boolean =
        ch.isLetterOrDigit() || ch == '-' || ch == '+' || ch == ':' || ch == ',' || ch == '.'

    private fun isDomainValue(token: String): Boolean =
        token.isNotBlank() &&
            !token.startsWith("-") &&
            token.all { it.isLetterOrDigit() || it == '-' || it == '_' || it == '.' }

    private fun isSignedInt(token: String): Boolean =
        token.toIntOrNull() != null

    private fun isUnsignedInt(token: String): Boolean =
        token.toIntOrNull()?.let { it >= 0 } == true

    private fun isModifierValue(token: String): Boolean =
        token.isNotBlank() && token.all(::isAttachedValueChar)
}
