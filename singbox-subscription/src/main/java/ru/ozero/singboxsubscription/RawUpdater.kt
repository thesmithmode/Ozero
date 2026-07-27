package ru.ozero.singboxsubscription

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.cert.CertPathValidatorException
import java.security.cert.CertificateException
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException
import ru.ozero.singboxconfig.BeanSupportDecision
import ru.ozero.singboxconfig.BeanSupportError
import ru.ozero.singboxconfig.ConfigBuilder
import ru.ozero.singboxfmt.AbstractBean
import ru.ozero.singboxfmt.KryoSerializer
import ru.ozero.singboxfmt.ShadowsocksBean
import ru.ozero.singboxfmt.StandardV2RayBean
import ru.ozero.singboxfmt.TrojanBean
import ru.ozero.singboxfmt.VMessBean
import ru.ozero.singboxfmt.VLESSBean
import ru.ozero.singboxfmt.protocolLabel
import ru.ozero.singboxroom.dao.ProxyProfileDao
import ru.ozero.singboxroom.dao.SubscriptionGroupDao
import ru.ozero.singboxroom.entity.ProxyProfile
import ru.ozero.singboxroom.entity.SubscriptionGroup
import ru.ozero.singboxsubscription.parser.Base64BundleParser
import ru.ozero.singboxsubscription.parser.RawShareLinksParser
import ru.ozero.singboxsubscription.parser.SubscriptionInfoParser

class RawUpdater(
    private val okHttpClient: OkHttpClient,
    private val groupDao: SubscriptionGroupDao,
    private val profileDao: ProxyProfileDao,
    private val userCaOkHttpClient: OkHttpClient = okHttpClient,
    private val insecureOkHttpClient: OkHttpClient = okHttpClient,
) {
    suspend fun refresh(group: SubscriptionGroup): Result<Int> = withContext(Dispatchers.IO) {
        val lastAttemptAt = System.currentTimeMillis()
        val attemptedGroup = group.copy(lastAttemptAt = lastAttemptAt)
        val result = runCatching<Int> {
            groupDao.update(attemptedGroup)
            val request = Request.Builder()
                .url(group.subscriptionUrl)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/plain, application/json, application/yaml, text/yaml, */*")
                .build()
            executeRequest(group, request).use { response ->
                if (!response.isSuccessful) {
                    throw SubscriptionHttpException(response.code)
                }
                val body = response.body?.readUtf8Limited(MAX_SUBSCRIPTION_BYTES) ?: ""
                val subInfo = SubscriptionInfoParser.parse(response.header("Subscription-Userinfo"))

                val beans = Base64BundleParser.parse(body)
                    .ifEmpty { RawShareLinksParser.parse(body) }
                if (beans.isEmpty()) throw SubscriptionNoProfilesException()
                logSupportDiagnostics(group.id, beans)

                val profiles = beans.take(MAX_PROFILES_PER_GROUP).mapIndexed { idx, bean ->
                    ProxyProfile(
                        groupId = group.id,
                        name = bean.name.ifBlank { "Server ${idx + 1}" },
                        beanBlob = KryoSerializer.serialize(bean),
                        protocolType = protocolTypeOf(bean),
                        userOrder = idx,
                    )
                }
                val existingProfiles = profileDao.getAutoCandidatesByGroupId(group.id, MAX_PROFILES_PER_GROUP)
                val incomingBaseKeyCounts = profiles
                    .groupingBy { it.stableBaseIdentityKey() }
                    .eachCount()
                val existingByBaseIdentity = existingProfiles
                    .groupBy { it.stableBaseIdentityKey() }
                    .mapValues { (_, matches) -> matches.toMutableList() }
                val existingByFullIdentity = existingProfiles
                    .groupBy { it.stableFullIdentityKey() }
                    .mapValues { (_, matches) -> matches.toMutableList() }
                val profilesWithStableIds = profiles.map { profile ->
                    val baseKey = profile.stableBaseIdentityKey()
                    val useFullKey = (incomingBaseKeyCounts[baseKey] ?: 0) > 1 ||
                        ((existingByBaseIdentity[baseKey]?.size ?: 0) > 1)
                    val matched = if (useFullKey) {
                        existingByFullIdentity[profile.stableFullIdentityKey()]?.removeFirstOrNull()
                    } else {
                        existingByBaseIdentity[baseKey]?.removeFirstOrNull()
                    }
                    if (matched != null) {
                        profile.copy(
                            id = matched.id,
                            latencyMs = matched.latencyMs,
                            probeError = matched.probeError,
                            lastProbeAt = matched.lastProbeAt,
                        )
                    } else {
                        profile
                    }
                }

                profileDao.replaceForGroup(group.id, profilesWithStableIds)

                val currentGroup = groupDao.getById(group.id) ?: attemptedGroup
                val usedBytes = subInfo?.let { it.uploadBytes + it.downloadBytes } ?: currentGroup.bytesUsed
                val remainingBytes = subInfo?.let {
                    maxOf(0L, it.totalBytes - it.uploadBytes - it.downloadBytes)
                } ?: currentGroup.bytesRemaining
                groupDao.update(
                    currentGroup.copy(
                        lastUpdated = System.currentTimeMillis(),
                        lastAttemptAt = lastAttemptAt,
                        lastRefreshErrorCode = null,
                        lastServerCount = profilesWithStableIds.size,
                        bytesUsed = usedBytes,
                        bytesRemaining = remainingBytes,
                        expiryDate = subInfo?.expiryTimestamp ?: currentGroup.expiryDate,
                    ),
                )

                Log.i(TAG, "refresh ok groupId=${group.id} servers=${profilesWithStableIds.size}")
                profilesWithStableIds.size
            }
        }.recoverCatching { e ->
            throw normalizeError(e)
        }
        result.exceptionOrNull()?.let { failure ->
            val errorCode = refreshErrorCode(failure)
            runCatching {
                val currentGroup = groupDao.getById(group.id) ?: attemptedGroup
                groupDao.update(
                    currentGroup.copy(
                        lastAttemptAt = lastAttemptAt,
                        lastRefreshErrorCode = errorCode,
                    ),
                )
            }.onFailure { statusFailure ->
                if (statusFailure !== failure) failure.addSuppressed(statusFailure)
            }
            Log.w(
                TAG,
                "refresh failed groupId=${group.id} code=$errorCode " +
                    "causes=${failure.safeCauseDiagnostics()}",
            )
        }
        result
    }

    private fun httpClientFor(group: SubscriptionGroup): OkHttpClient = when {
        group.isBuiltin -> okHttpClient
        group.allowInsecureTls -> insecureOkHttpClient
        else -> userCaOkHttpClient
    }

    private fun executeRequest(group: SubscriptionGroup, request: Request): Response {
        return httpClientFor(group).newCall(request).execute()
    }

    companion object {
        private const val TAG = "RawUpdater"
        const val PROTOCOL_VLESS = 0
        const val PROTOCOL_VMESS = 1
        const val PROTOCOL_TROJAN = 2
        const val PROTOCOL_SHADOWSOCKS = 3

        private const val USER_AGENT = "mihomo/1.19.23"
        private const val MAX_PROFILES_PER_GROUP = 2_000
        private const val MAX_SUBSCRIPTION_BYTES = 16L * 1024 * 1024

        private fun logSupportDiagnostics(groupId: Long, beans: List<AbstractBean>) {
            val decisions = beans.map { bean -> bean to ConfigBuilder.supportDecision(bean) }
            val supportedCount = decisions.count { it.second is BeanSupportDecision.Supported }
            val rejected = decisions.mapNotNull { (bean, decision) ->
                (decision as? BeanSupportDecision.Unsupported)?.let { RejectedBean(bean, it.error) }
            }
            Log.i(
                TAG,
                "subscription parsed groupId=$groupId parsed=${beans.size} supported=$supportedCount " +
                    "rejected=${rejected.size} byProtocol=${rejected.groupByProtocol()} " +
                    "byTransport=${rejected.groupByTransport()} byReason=${rejected.groupByReason()}",
            )
        }

        private data class RejectedBean(val bean: AbstractBean, val error: BeanSupportError)

        private fun List<RejectedBean>.groupByProtocol(): String =
            groupingBy { it.bean.protocolLabel() }.eachCount().stableDiagnosticString()

        private fun List<RejectedBean>.groupByTransport(): String =
            groupingBy { (it.bean as? StandardV2RayBean)?.type.orEmpty().ifBlank { "none" } }
                .eachCount()
                .stableDiagnosticString()

        private fun List<RejectedBean>.groupByReason(): String =
            groupingBy { it.error.name }.eachCount().stableDiagnosticString()

        private fun Map<String, Int>.stableDiagnosticString(): String =
            entries.sortedBy { it.key }.joinToString(prefix = "{", postfix = "}") { "${it.key}=${it.value}" }

        private fun normalizeError(e: Throwable): Throwable = when {
            e.isSubscriptionCertificateFailure() ->
                SSLHandshakeException("Subscription TLS certificate validation failed").initCause(e)
            else -> e
        }

        private fun refreshErrorCode(error: Throwable): String = when {
            error.isSubscriptionCertificateFailure() -> SubscriptionRefreshErrorCode.TLS_CERTIFICATE
            error.causeChain().any { it is SocketTimeoutException } -> SubscriptionRefreshErrorCode.TIMEOUT
            error.causeChain().any { it is UnknownHostException } -> SubscriptionRefreshErrorCode.DNS
            error.causeChain().any { it is SubscriptionHttpException } -> SubscriptionRefreshErrorCode.HTTP
            error.causeChain().any { it is SubscriptionNoProfilesException } ->
                SubscriptionRefreshErrorCode.NO_PROFILES
            error.causeChain().any { it is SubscriptionBodyTooLargeException } ->
                SubscriptionRefreshErrorCode.BODY_TOO_LARGE
            error.causeChain().any { it is IllegalArgumentException } -> SubscriptionRefreshErrorCode.INVALID_URL
            error.causeChain().any { it is IOException } -> SubscriptionRefreshErrorCode.NETWORK
            else -> SubscriptionRefreshErrorCode.UNKNOWN
        }

        private fun Throwable.safeCauseDiagnostics(): String {
            val causes = causeChain()
                .map { cause -> cause.safeCauseLabel() }
                .distinct()
                .joinToString(prefix = "[", postfix = "]")
            return "chain=$causes"
        }

        private fun Throwable.safeCauseLabel(): String {
            val text = message.orEmpty().lowercase()
            val flags = listOfNotNull(
                "expired".takeIf { text.contains("expired") || text.contains("not after") },
                "hostname_mismatch".takeIf { text.contains("hostname") || text.contains("peer not authenticated") },
                "trust_anchor".takeIf { text.contains("trust anchor") },
                "certificate_path_validation".takeIf {
                    this is CertPathValidatorException || text.contains("certpath") || text.contains("pkix")
                },
            )
            return if (flags.isEmpty()) {
                javaClass.simpleName
            } else {
                "${javaClass.simpleName}:${flags.joinToString(",")}"
            }
        }

        private fun Throwable.isSubscriptionCertificateFailure(): Boolean {
            if (this is SSLPeerUnverifiedException) return true
            if (this !is SSLHandshakeException) return false
            return causeChain().any { cause ->
                cause is CertificateException ||
                    cause is CertPathValidatorException ||
                    cause.message.orEmpty().containsCertificateFailureText()
            } ||
                message.orEmpty().containsCertificateFailureText()
        }

        private fun Throwable.causeChain(): Sequence<Throwable> = generateSequence(this) { it.cause }

        private fun String.containsCertificateFailureText(): Boolean {
            val text = lowercase()
            return text.contains("chain validation failed") ||
                text.contains("trust anchor") ||
                text.contains("pkix") ||
                text.contains("certpath") ||
                text.contains("certificate")
        }

        fun protocolTypeOf(bean: ru.ozero.singboxfmt.AbstractBean): Int = when (bean) {
            is ru.ozero.singboxfmt.VLESSBean -> PROTOCOL_VLESS
            is VMessBean -> PROTOCOL_VMESS
            is TrojanBean -> PROTOCOL_TROJAN
            is ShadowsocksBean -> PROTOCOL_SHADOWSOCKS
            else -> PROTOCOL_VLESS
        }
    }
}

private fun ResponseBody.readUtf8Limited(maxBytes: Long): String {
    val declaredLength = contentLength()
    if (declaredLength > maxBytes) {
        throw SubscriptionBodyTooLargeException()
    }
    val out = ByteArrayOutputStream(minOf(maxBytes, 8_192L).toInt())
    byteStream().use { input ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read == -1) break
            total += read.toLong()
            if (total > maxBytes) {
                throw SubscriptionBodyTooLargeException()
            }
            out.write(buffer, 0, read)
        }
    }
    val charset = contentType()?.charset(Charsets.UTF_8) ?: Charsets.UTF_8
    return out.toString(charset.name())
}

object SubscriptionRefreshErrorCode {
    const val TLS_CERTIFICATE = "tls_certificate"
    const val TIMEOUT = "timeout"
    const val DNS = "dns"
    const val HTTP = "http"
    const val NO_PROFILES = "no_profiles"
    const val BODY_TOO_LARGE = "body_too_large"
    const val INVALID_URL = "invalid_url"
    const val NETWORK = "network"
    const val UNKNOWN = "unknown"
}

private class SubscriptionHttpException(statusCode: Int) : IOException("Subscription HTTP $statusCode")

private class SubscriptionNoProfilesException : IOException("Subscription contains no supported servers")

private class SubscriptionBodyTooLargeException : IOException("Subscription body too large")

private fun ProxyProfile.stableBaseIdentityKey(): String =
    listOf(
        groupId.toString(),
        protocolType.toString(),
        runCatching { KryoSerializer.deserialize<AbstractBean>(beanBlob) }
            .getOrNull()
            ?.let { "${it.serverAddress}|${it.serverPort}|${it.stableCredentialKey()}" }
            ?: beanBlob.contentHashCode().toString(),
    ).joinToString("|")

private fun ProxyProfile.stableFullIdentityKey(): String =
    listOf(
        stableBaseIdentityKey(),
        runCatching { KryoSerializer.deserialize<AbstractBean>(beanBlob) }
            .getOrNull()
            ?.stableRuntimeKey()
            ?: "",
    ).joinToString("|")

private fun AbstractBean.stableCredentialKey(): String = when (this) {
    is VLESSBean -> "uuid=${uuid.trim()}"
    is VMessBean -> "uuid=${uuid.trim()}"
    is TrojanBean -> "password=${password.trim()}"
    is ShadowsocksBean -> "method=${method.trim()}|password=${password.trim()}"
    is StandardV2RayBean -> "uuid=${uuid.trim()}"
    else -> "blob=${KryoSerializer.serialize(this).contentHashCode()}"
}

private fun AbstractBean.stableRuntimeKey(): String = when (this) {
    is VLESSBean -> listOf(
        standardV2RayRuntimeKey(),
        "flow=${flow.trim()}",
    ).joinToString("|")
    is VMessBean -> listOf(
        standardV2RayRuntimeKey(),
        "alterId=$alterId",
        "encryption=${encryption.trim()}",
    ).joinToString("|")
    is StandardV2RayBean -> standardV2RayRuntimeKey()
    is ShadowsocksBean -> listOf(
        "plugin=${plugin.trim()}",
        "pluginOpts=${pluginOpts.trim()}",
    ).joinToString("|")
    else -> ""
}

internal fun stableIdentityKeysForTest(bean: AbstractBean, groupId: Long = 1L): Pair<String, String> {
    val profile = ProxyProfile(
        groupId = groupId,
        name = bean.name,
        beanBlob = KryoSerializer.serialize(bean),
        protocolType = RawUpdater.protocolTypeOf(bean),
    )
    return profile.stableBaseIdentityKey() to profile.stableFullIdentityKey()
}

internal fun stableBeanKeysForTest(bean: AbstractBean): Pair<String, String> =
    bean.stableCredentialKey() to bean.stableRuntimeKey()

internal fun corruptedStableIdentityKeysForTest(blob: ByteArray, groupId: Long = 1L): Pair<String, String> {
    val profile = ProxyProfile(
        groupId = groupId,
        name = "corrupted",
        beanBlob = blob,
        protocolType = RawUpdater.PROTOCOL_VLESS,
    )
    return profile.stableBaseIdentityKey() to profile.stableFullIdentityKey()
}

private fun StandardV2RayBean.standardV2RayRuntimeKey(): String =
    listOf(
        "type=${type.trim()}",
        "security=${security.trim()}",
        "sni=${sni.trim()}",
        "host=${host.trim()}",
        "path=${path.trim()}",
        "grpcServiceName=${grpcServiceName.trim()}",
        "maxEarlyData=$maxEarlyData",
        "earlyDataHeaderName=${earlyDataHeaderName.trim()}",
        "splithttpMode=${splithttpMode.trim()}",
        "headerType=${headerType.trim()}",
        "mKcpSeed=${mKcpSeed.trim()}",
        "quicSecurity=${quicSecurity.trim()}",
        "quicKey=${quicKey.trim()}",
        "alpn=${alpn.trim()}",
        "allowInsecure=$allowInsecure",
        "utlsFingerprint=${utlsFingerprint.trim()}",
        "realityPublicKey=${realityPublicKey.trim()}",
        "realityShortId=${realityShortId.trim()}",
    ).joinToString("|")
