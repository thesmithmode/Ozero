package ru.ozero.coresubscriptions

sealed class SubscriptionFetchResult {
    data class Success(val body: ByteArray, val signature: ByteArray?) : SubscriptionFetchResult() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Success) return false
            return body.contentEquals(other.body) &&
                (signature?.contentEquals(other.signature ?: ByteArray(0)) ?: (other.signature == null))
        }

        override fun hashCode(): Int {
            var result = body.contentHashCode()
            result = 31 * result + (signature?.contentHashCode() ?: 0)
            return result
        }
    }

    data class Failure(val reason: String, val statusCode: Int? = null) : SubscriptionFetchResult()
}

interface SubscriptionSource {
    suspend fun fetch(url: String): SubscriptionFetchResult
}
