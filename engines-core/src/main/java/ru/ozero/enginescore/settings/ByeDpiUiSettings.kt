package ru.ozero.enginescore.settings

import org.json.JSONObject

data class ByeDpiUiSettings(
    val maxConnections: Int = DEFAULT_MAX_CONNECTIONS,
    val bufferSize: Int = DEFAULT_BUFFER_SIZE,
    val defaultTtl: Int = DEFAULT_DEFAULT_TTL,
    val noDomain: Boolean = DEFAULT_NO_DOMAIN,
    val desyncHttp: Boolean = DEFAULT_DESYNC_HTTP,
    val desyncHttps: Boolean = DEFAULT_DESYNC_HTTPS,
    val desyncUdp: Boolean = DEFAULT_DESYNC_UDP,
    val desyncMethod: DesyncMethod = DEFAULT_DESYNC_METHOD,
    val splitPosition: Int = DEFAULT_SPLIT_POSITION,
    val splitAtHost: Boolean = DEFAULT_SPLIT_AT_HOST,
    val fakeTtl: Int = DEFAULT_FAKE_TTL,
    val fakeSni: String = DEFAULT_FAKE_SNI,
    val fakeOffset: Int = DEFAULT_FAKE_OFFSET,
    val oobChar: String = DEFAULT_OOB_CHAR,
    val hostMixedCase: Boolean = DEFAULT_HOST_MIXED_CASE,
    val domainMixedCase: Boolean = DEFAULT_DOMAIN_MIXED_CASE,
    val hostRemoveSpaces: Boolean = DEFAULT_HOST_REMOVE_SPACES,
    val tlsRecordSplit: Boolean = DEFAULT_TLS_RECORD_SPLIT,
    val tlsRecordSplitPosition: Int = DEFAULT_TLS_RECORD_SPLIT_POSITION,
    val tlsRecordSplitAtSni: Boolean = DEFAULT_TLS_RECORD_SPLIT_AT_SNI,
    val tcpFastOpen: Boolean = DEFAULT_TCP_FAST_OPEN,
    val udpFakeCount: Int = DEFAULT_UDP_FAKE_COUNT,
    val dropSack: Boolean = DEFAULT_DROP_SACK,
) {
    enum class DesyncMethod { NONE, SPLIT, DISORDER, FAKE, OOB, DISOOB }

    fun toJson(): String = JSONObject().apply {
        put(KEY_MAX_CONNECTIONS, maxConnections)
        put(KEY_BUFFER_SIZE, bufferSize)
        put(KEY_DEFAULT_TTL, defaultTtl)
        put(KEY_NO_DOMAIN, noDomain)
        put(KEY_DESYNC_HTTP, desyncHttp)
        put(KEY_DESYNC_HTTPS, desyncHttps)
        put(KEY_DESYNC_UDP, desyncUdp)
        put(KEY_DESYNC_METHOD, desyncMethod.name)
        put(KEY_SPLIT_POSITION, splitPosition)
        put(KEY_SPLIT_AT_HOST, splitAtHost)
        put(KEY_FAKE_TTL, fakeTtl)
        put(KEY_FAKE_SNI, fakeSni)
        put(KEY_FAKE_OFFSET, fakeOffset)
        put(KEY_OOB_CHAR, oobChar)
        put(KEY_HOST_MIXED_CASE, hostMixedCase)
        put(KEY_DOMAIN_MIXED_CASE, domainMixedCase)
        put(KEY_HOST_REMOVE_SPACES, hostRemoveSpaces)
        put(KEY_TLS_RECORD_SPLIT, tlsRecordSplit)
        put(KEY_TLS_RECORD_SPLIT_POSITION, tlsRecordSplitPosition)
        put(KEY_TLS_RECORD_SPLIT_AT_SNI, tlsRecordSplitAtSni)
        put(KEY_TCP_FAST_OPEN, tcpFastOpen)
        put(KEY_UDP_FAKE_COUNT, udpFakeCount)
        put(KEY_DROP_SACK, dropSack)
    }.toString()

    companion object {
        const val DEFAULT_MAX_CONNECTIONS: Int = 512
        const val DEFAULT_BUFFER_SIZE: Int = 16384
        const val DEFAULT_DEFAULT_TTL: Int = 0
        const val DEFAULT_NO_DOMAIN: Boolean = false
        const val DEFAULT_DESYNC_HTTP: Boolean = true
        const val DEFAULT_DESYNC_HTTPS: Boolean = true
        const val DEFAULT_DESYNC_UDP: Boolean = true
        val DEFAULT_DESYNC_METHOD: DesyncMethod = DesyncMethod.OOB
        const val DEFAULT_SPLIT_POSITION: Int = 1
        const val DEFAULT_SPLIT_AT_HOST: Boolean = false
        const val DEFAULT_FAKE_TTL: Int = 8
        const val DEFAULT_FAKE_SNI: String = "www.iana.org"
        const val DEFAULT_FAKE_OFFSET: Int = 0
        const val DEFAULT_OOB_CHAR: String = "a"
        const val DEFAULT_HOST_MIXED_CASE: Boolean = false
        const val DEFAULT_DOMAIN_MIXED_CASE: Boolean = false
        const val DEFAULT_HOST_REMOVE_SPACES: Boolean = false
        const val DEFAULT_TLS_RECORD_SPLIT: Boolean = false
        const val DEFAULT_TLS_RECORD_SPLIT_POSITION: Int = 0
        const val DEFAULT_TLS_RECORD_SPLIT_AT_SNI: Boolean = false
        const val DEFAULT_TCP_FAST_OPEN: Boolean = false
        const val DEFAULT_UDP_FAKE_COUNT: Int = 1
        const val DEFAULT_DROP_SACK: Boolean = false

        val DEFAULT: ByeDpiUiSettings = ByeDpiUiSettings()

        private const val KEY_MAX_CONNECTIONS = "maxConnections"
        private const val KEY_BUFFER_SIZE = "bufferSize"
        private const val KEY_DEFAULT_TTL = "defaultTtl"
        private const val KEY_NO_DOMAIN = "noDomain"
        private const val KEY_DESYNC_HTTP = "desyncHttp"
        private const val KEY_DESYNC_HTTPS = "desyncHttps"
        private const val KEY_DESYNC_UDP = "desyncUdp"
        private const val KEY_DESYNC_METHOD = "desyncMethod"
        private const val KEY_SPLIT_POSITION = "splitPosition"
        private const val KEY_SPLIT_AT_HOST = "splitAtHost"
        private const val KEY_FAKE_TTL = "fakeTtl"
        private const val KEY_FAKE_SNI = "fakeSni"
        private const val KEY_FAKE_OFFSET = "fakeOffset"
        private const val KEY_OOB_CHAR = "oobChar"
        private const val KEY_HOST_MIXED_CASE = "hostMixedCase"
        private const val KEY_DOMAIN_MIXED_CASE = "domainMixedCase"
        private const val KEY_HOST_REMOVE_SPACES = "hostRemoveSpaces"
        private const val KEY_TLS_RECORD_SPLIT = "tlsRecordSplit"
        private const val KEY_TLS_RECORD_SPLIT_POSITION = "tlsRecordSplitPosition"
        private const val KEY_TLS_RECORD_SPLIT_AT_SNI = "tlsRecordSplitAtSni"
        private const val KEY_TCP_FAST_OPEN = "tcpFastOpen"
        private const val KEY_UDP_FAKE_COUNT = "udpFakeCount"
        private const val KEY_DROP_SACK = "dropSack"

        @Suppress("LongMethod", "ComplexMethod")
        fun fromJson(raw: String?): ByeDpiUiSettings {
            if (raw.isNullOrBlank()) return DEFAULT
            val obj = runCatching { JSONObject(raw) }.getOrNull() ?: return DEFAULT
            return ByeDpiUiSettings(
                maxConnections = obj.optInt(KEY_MAX_CONNECTIONS, DEFAULT_MAX_CONNECTIONS),
                bufferSize = obj.optInt(KEY_BUFFER_SIZE, DEFAULT_BUFFER_SIZE),
                defaultTtl = obj.optInt(KEY_DEFAULT_TTL, DEFAULT_DEFAULT_TTL),
                noDomain = obj.optBoolean(KEY_NO_DOMAIN, DEFAULT_NO_DOMAIN),
                desyncHttp = obj.optBoolean(KEY_DESYNC_HTTP, DEFAULT_DESYNC_HTTP),
                desyncHttps = obj.optBoolean(KEY_DESYNC_HTTPS, DEFAULT_DESYNC_HTTPS),
                desyncUdp = obj.optBoolean(KEY_DESYNC_UDP, DEFAULT_DESYNC_UDP),
                desyncMethod = runCatching {
                    DesyncMethod.valueOf(obj.optString(KEY_DESYNC_METHOD, DEFAULT_DESYNC_METHOD.name))
                }.getOrDefault(DEFAULT_DESYNC_METHOD),
                splitPosition = obj.optInt(KEY_SPLIT_POSITION, DEFAULT_SPLIT_POSITION),
                splitAtHost = obj.optBoolean(KEY_SPLIT_AT_HOST, DEFAULT_SPLIT_AT_HOST),
                fakeTtl = obj.optInt(KEY_FAKE_TTL, DEFAULT_FAKE_TTL),
                fakeSni = obj.optString(KEY_FAKE_SNI, DEFAULT_FAKE_SNI),
                fakeOffset = obj.optInt(KEY_FAKE_OFFSET, DEFAULT_FAKE_OFFSET),
                oobChar = obj.optString(KEY_OOB_CHAR, DEFAULT_OOB_CHAR).take(1).ifEmpty { DEFAULT_OOB_CHAR },
                hostMixedCase = obj.optBoolean(KEY_HOST_MIXED_CASE, DEFAULT_HOST_MIXED_CASE),
                domainMixedCase = obj.optBoolean(KEY_DOMAIN_MIXED_CASE, DEFAULT_DOMAIN_MIXED_CASE),
                hostRemoveSpaces = obj.optBoolean(KEY_HOST_REMOVE_SPACES, DEFAULT_HOST_REMOVE_SPACES),
                tlsRecordSplit = obj.optBoolean(KEY_TLS_RECORD_SPLIT, DEFAULT_TLS_RECORD_SPLIT),
                tlsRecordSplitPosition = obj.optInt(
                    KEY_TLS_RECORD_SPLIT_POSITION, DEFAULT_TLS_RECORD_SPLIT_POSITION,
                ),
                tlsRecordSplitAtSni = obj.optBoolean(
                    KEY_TLS_RECORD_SPLIT_AT_SNI, DEFAULT_TLS_RECORD_SPLIT_AT_SNI,
                ),
                tcpFastOpen = obj.optBoolean(KEY_TCP_FAST_OPEN, DEFAULT_TCP_FAST_OPEN),
                udpFakeCount = obj.optInt(KEY_UDP_FAKE_COUNT, DEFAULT_UDP_FAKE_COUNT),
                dropSack = obj.optBoolean(KEY_DROP_SACK, DEFAULT_DROP_SACK),
            )
        }
    }
}
