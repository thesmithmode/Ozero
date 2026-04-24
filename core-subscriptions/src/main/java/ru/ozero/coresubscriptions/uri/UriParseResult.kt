package ru.ozero.coresubscriptions.uri

sealed class UriParseResult<out T> {
    data class Ok<T>(val server: T) : UriParseResult<T>()
    data class Error(val reason: String) : UriParseResult<Nothing>()
}
