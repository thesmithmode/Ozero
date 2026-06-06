package ru.ozero.enginebyedpi.strategy

object ByeDpiArgvValidator {
    fun isValid(command: String): Boolean {
        val tokens = ByeDpiOptionBlocks.tokenize(command)
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
                    if (!isLongOptionToken(token)) return false
                    val rawName = token.substringBefore('=')
                    if (token.contains("=")) {
                        if (!isValueValid(rawName, token.substringAfter('='))) return false
                    } else if (ByeDpiOptionBlocks.requiresDetachedValue(rawName)) {
                        expectedValueFor = token
                    }
                }
                token.startsWith("-") -> {
                    if (!isFlagToken(token)) return false
                    if (ByeDpiOptionBlocks.requiresDetachedValue(token)) {
                        expectedValueFor = token
                    }
                }
                else -> return false
            }
        }
        return expectedValueFor == null
    }

    private fun isValueValid(option: String, token: String): Boolean =
        when (option) {
            "-n" -> isDomainValue(token)
            "--fake" -> isSignedInt(token)
            "--ttl" -> isUnsignedInt(token)
            "--split", "--disorder" -> isModifierValue(token)
            "-a", "-d", "-e", "-f", "-m", "-o", "-p", "-q", "-r", "-s", "-t", "-O", "-R" ->
                isModifierValue(token) && !token.startsWith("-")
            else -> token.isNotBlank()
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
                token.length == 2 || token.length > 2 && token.drop(2).all(::isAttachedValueChar)
            else -> false
        }
    }

    private fun isLongOptionToken(token: String): Boolean {
        val rawName = token.substringBefore('=')
        if (rawName.length <= 2) return false
        if (!rawName.drop(2).all { it.isLowerCase() || it.isDigit() || it == '-' }) return false
        return token.indexOf('=') < 0 || token.substringAfter('=').isNotBlank()
    }

    private fun isAttachedValueChar(ch: Char): Boolean =
        ch.isLetterOrDigit() || ch == '-' || ch == '+' || ch == ':' || ch == ',' || ch == '.'

    private fun isDomainValue(token: String): Boolean {
        val value = token.removeSurrounding("\"")
        return value.isNotBlank() &&
            !value.startsWith("-") &&
            value.all { it.isLetterOrDigit() || it == '-' || it == '_' || it == '.' }
    }

    private fun isSignedInt(token: String): Boolean =
        token.toIntOrNull() != null

    private fun isUnsignedInt(token: String): Boolean =
        token.toIntOrNull()?.let { it >= 0 } == true

    private fun isModifierValue(token: String): Boolean =
        token.isNotBlank() && token.all(::isAttachedValueChar)
}
