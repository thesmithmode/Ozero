package ru.ozero.enginescore

object ByeDpiArgs {
    const val MAX_LENGTH = 16 * 1024
    const val MAX_TOKENS = 256

    fun validate(args: String): Boolean = args.length <= MAX_LENGTH && tokenCount(args) <= MAX_TOKENS

    fun tokens(args: String): List<String> {
        require(args.length <= MAX_LENGTH) { "ByeDPI args are too long" }
        val result = mutableListOf<String>()
        var start = -1
        for (index in args.indices) {
            if (args[index].isWhitespace()) {
                if (start >= 0) {
                    result += args.substring(start, index)
                    require(result.size <= MAX_TOKENS) { "ByeDPI args have too many tokens" }
                    start = -1
                }
            } else if (start < 0) {
                start = index
            }
        }
        if (start >= 0) {
            result += args.substring(start)
            require(result.size <= MAX_TOKENS) { "ByeDPI args have too many tokens" }
        }
        return result
    }

    private fun tokenCount(args: String): Int {
        var count = 0
        var inToken = false
        for (ch in args) {
            if (ch.isWhitespace()) {
                inToken = false
            } else if (!inToken) {
                count++
                if (count > MAX_TOKENS) return count
                inToken = true
            }
        }
        return count
    }
}
