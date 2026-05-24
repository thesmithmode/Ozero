package ru.ozero.singboxsubscription.parser

data class SubscriptionInfo(
    val uploadBytes: Long = 0,
    val downloadBytes: Long = 0,
    val totalBytes: Long = 0,
    val expiryTimestamp: Long = 0,
)

object SubscriptionInfoParser {
    private val fieldRegex = Regex("""(\w+)=(\d+)""")

    fun parse(header: String?): SubscriptionInfo? {
        if (header.isNullOrBlank()) return null
        val fields = fieldRegex.findAll(header)
            .associate { it.groupValues[1] to it.groupValues[2].toLongOrNull() }
        if (fields.isEmpty()) return null
        return SubscriptionInfo(
            uploadBytes = fields["upload"] ?: 0L,
            downloadBytes = fields["download"] ?: 0L,
            totalBytes = fields["total"] ?: 0L,
            expiryTimestamp = fields["expire"] ?: 0L,
        )
    }
}
