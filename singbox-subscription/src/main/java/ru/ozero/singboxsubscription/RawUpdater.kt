package ru.ozero.singboxsubscription

import android.util.Log
import androidx.room.withTransaction
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
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
import java.util.concurrent.ConcurrentHashMap
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException
import kotlin.coroutines.resume
import ru.ozero.singboxconfig.BeanSupportDecision
import ru.ozero.singboxconfig.BeanSupportError
import ru.ozero.singboxconfig.ConfigBuilder
import ru.ozero.singboxconfig.PersistedProfileRecovery
import ru.ozero.singboxfmt.AbstractBean
import ru.ozero.singboxfmt.KryoSerializer
import ru.ozero.singboxfmt.ShadowsocksBean
import ru.ozero.singboxfmt.StandardV2RayBean
import ru.ozero.singboxfmt.TrojanBean
import ru.ozero.singboxfmt.VMessBean
import ru.ozero.singboxfmt.VLESSBean
import ru.ozero.singboxfmt.normalizeSingboxTransport
import ru.ozero.singboxfmt.protocolLabel
import ru.ozero.singboxroom.SingboxDatabase
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
    private val database: SingboxDatabase? = null,
    private val onProfilesRemoved: suspend (Set<Long>) -> Unit = {},
) {
    private val refreshLocks = ConcurrentHashMap<Long, Mutex>()

    suspend fun refresh(group: SubscriptionGroup, allowInsecureRetry: Boolean = false): Result<Int> =
        refreshLocks.computeIfAbsent(group.id) { Mutex() }.withLock {
            refreshLocked(group, allowInsecureRetry)
        }

    @Suppress("LongMethod", "CyclomaticComplexMethod", "CognitiveComplexMethod")
    private suspend fun refreshLocked(
        group: SubscriptionGroup,
        allowInsecureRetry: Boolean,
    ): Result<Int> = withContext(Dispatchers.IO) {
        val lastAttemptAt = System.currentTimeMillis()
        val refreshGeneration = beginRefresh(group.id, lastAttemptAt)
            ?: return@withContext Result.failure(SubscriptionRefreshStaleException())
        Log.i(
            TAG,
            "refresh started groupId=${group.id} generation=$refreshGeneration " +
                "tlsMode=${group.subscriptionTlsMode()}",
        )
        val result = runCatching<Int> {
            val request = Request.Builder()
                .url(group.subscriptionUrl)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/plain, application/json, application/yaml, text/yaml, */*")
                .build()
            executeRequest(group, request, allowInsecureRetry).use { response ->
                if (!response.isSuccessful) {
                    throw SubscriptionHttpException(response.code)
                }
                val body = response.body?.readUtf8Limited(MAX_SUBSCRIPTION_BYTES) ?: ""
                Log.i(
                    TAG,
                    "refresh response groupId=${group.id} generation=$refreshGeneration " +
                        "http=${response.code} bodyChars=${body.length}",
                )
                val subInfo = SubscriptionInfoParser.parse(response.header("Subscription-Userinfo"))

                val parsedBeans = Base64BundleParser.parse(body)
                    .ifEmpty { RawShareLinksParser.parse(body) }
                if (parsedBeans.isEmpty()) throw SubscriptionNoProfilesException()
                val parsedWindow = parsedBeans.take(MAX_PROFILES_PER_GROUP)
                logSupportDiagnostics(group.id, parsedWindow)
                val beans = parsedWindow.filter { ConfigBuilder.supportDecision(it) is BeanSupportDecision.Supported }
                if (beans.isEmpty()) throw SubscriptionNoProfilesException()

                val profiles = beans.mapIndexed { idx, bean ->
                    ProxyProfile(
                        groupId = group.id,
                        name = bean.name.ifBlank { "Server ${idx + 1}" },
                        beanBlob = KryoSerializer.serialize(bean),
                        protocolType = protocolTypeOf(bean),
                        userOrder = idx,
                    )
                }.distinctBy { it.stableFullIdentityKey() }
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
                val currentGroup = groupDao.getById(group.id)
                    ?: group.copy(lastAttemptAt = lastAttemptAt, refreshGeneration = refreshGeneration)
                val usedBytes = subInfo?.let { it.uploadBytes + it.downloadBytes } ?: currentGroup.bytesUsed
                val remainingBytes = subInfo?.let {
                    maxOf(0L, it.totalBytes - it.uploadBytes - it.downloadBytes)
                } ?: currentGroup.bytesRemaining
                val updatedGroup = currentGroup.copy(
                    lastUpdated = System.currentTimeMillis(),
                    lastAttemptAt = lastAttemptAt,
                    lastRefreshErrorCode = null,
                    lastServerCount = profilesWithStableIds.size,
                    bytesUsed = usedBytes,
                    bytesRemaining = remainingBytes,
                    expiryDate = subInfo?.expiryTimestamp ?: currentGroup.expiryDate,
                )
                val committed = if (database != null) {
                    var didCommit = false
                    var removedProfileIds = emptySet<Long>()
                    database.withTransaction {
                        val current = groupDao.getById(group.id)
                        if (current?.refreshGeneration == refreshGeneration) {
                            removedProfileIds = profileDao.replaceForGroupAndReturnRemovedIds(
                                group.id,
                                profilesWithStableIds,
                            )
                            didCommit = groupDao.commitRefresh(
                                id = group.id,
                                refreshGeneration = refreshGeneration,
                                lastUpdated = updatedGroup.lastUpdated,
                                lastAttemptAt = updatedGroup.lastAttemptAt,
                                lastRefreshErrorCode = updatedGroup.lastRefreshErrorCode,
                                lastServerCount = updatedGroup.lastServerCount,
                                bytesUsed = updatedGroup.bytesUsed,
                                bytesRemaining = updatedGroup.bytesRemaining,
                                expiryDate = updatedGroup.expiryDate,
                            ) == 1
                        }
                    }
                    if (didCommit && removedProfileIds.isNotEmpty()) onProfilesRemoved(removedProfileIds)
                    didCommit
                } else {
                    if (groupDao.getById(group.id)?.refreshGeneration != refreshGeneration) {
                        false
                    } else {
                        val removedProfileIds = profileDao.replaceForGroupAndReturnRemovedIds(
                            group.id,
                            profilesWithStableIds,
                        )
                        val didCommit = groupDao.commitRefresh(
                            id = group.id,
                            refreshGeneration = refreshGeneration,
                            lastUpdated = updatedGroup.lastUpdated,
                            lastAttemptAt = updatedGroup.lastAttemptAt,
                            lastRefreshErrorCode = updatedGroup.lastRefreshErrorCode,
                            lastServerCount = updatedGroup.lastServerCount,
                            bytesUsed = updatedGroup.bytesUsed,
                            bytesRemaining = updatedGroup.bytesRemaining,
                            expiryDate = updatedGroup.expiryDate,
                        ) == 1
                        if (didCommit && removedProfileIds.isNotEmpty()) {
                            onProfilesRemoved(removedProfileIds)
                        }
                        didCommit
                    }
                }
                if (!committed) {
                    Log.i(TAG, "refresh superseded groupId=${group.id} generation=$refreshGeneration")
                    return@use 0
                }
                Log.i(
                    TAG,
                    "refresh committed groupId=${group.id} generation=$refreshGeneration " +
                        "servers=${profilesWithStableIds.size}",
                )
                profilesWithStableIds.size
            }
        }.recoverCatching { e ->
            throw normalizeError(e)
        }
        result.exceptionOrNull()?.let { failure ->
            if (failure is CancellationException) throw failure
        }
        result.exceptionOrNull()?.let { failure ->
            recordRefreshFailure(group.id, refreshGeneration, lastAttemptAt, failure)
        }
        result
    }

    private suspend fun beginRefresh(groupId: Long, attemptAt: Long): Long? {
        repeat(MAX_BEGIN_ATTEMPTS) {
            val current = groupDao.getById(groupId) ?: return null
            val nextGeneration = current.refreshGeneration + 1
            if (groupDao.tryBeginRefresh(groupId, current.refreshGeneration, attemptAt) == 1) {
                return nextGeneration
            }
        }
        return null
    }

    private suspend fun recordRefreshFailure(
        groupId: Long,
        refreshGeneration: Long,
        lastAttemptAt: Long,
        failure: Throwable,
    ) {
        val errorCode = refreshErrorCode(failure)
        runCatching {
            val currentGroup = groupDao.getById(groupId)
            if (currentGroup?.refreshGeneration == refreshGeneration) {
                groupDao.commitRefresh(
                    id = groupId,
                    refreshGeneration = refreshGeneration,
                    lastUpdated = currentGroup.lastUpdated,
                    lastAttemptAt = lastAttemptAt,
                    lastRefreshErrorCode = errorCode,
                    lastServerCount = currentGroup.lastServerCount,
                    bytesUsed = currentGroup.bytesUsed,
                    bytesRemaining = currentGroup.bytesRemaining,
                    expiryDate = currentGroup.expiryDate,
                )
            }
        }.onFailure { statusFailure ->
            if (statusFailure !== failure) failure.addSuppressed(statusFailure)
        }
        Log.w(
            TAG,
            "refresh failed groupId=$groupId code=$errorCode " +
                "causes=${failure.safeCauseDiagnostics()}",
        )
    }

    private fun httpClientFor(group: SubscriptionGroup, allowInsecureRetry: Boolean): OkHttpClient = when {
        allowInsecureRetry -> insecureOkHttpClient
        group.isBuiltin -> okHttpClient
        else -> userCaOkHttpClient
    }

    private suspend fun executeRequest(
        group: SubscriptionGroup,
        request: Request,
        allowInsecureRetry: Boolean,
    ): Response =
        suspendCancellableCoroutine { continuation ->
            val call = httpClientFor(group, allowInsecureRetry).newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, error: IOException) {
                    if (continuation.isActive) continuation.resumeWith(Result.failure(error))
                }

                override fun onResponse(call: Call, response: Response) {
                    if (!continuation.isActive) {
                        response.close()
                    } else {
                        continuation.resume(response) { _, pendingResponse, _ -> pendingResponse.close() }
                    }
                }
            })
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
        private const val MAX_BEGIN_ATTEMPTS = 8

        private fun logSupportDiagnostics(groupId: Long, beans: List<AbstractBean>) {
            val decisions = beans.map { bean ->
                Triple(bean, 0L, ConfigBuilder.supportDecision(bean))
            }
            val supportedCount = decisions.count { it.third is BeanSupportDecision.Supported }
            val rejected = decisions.mapNotNull { (bean, profileId, decision) ->
                (decision as? BeanSupportDecision.Unsupported)?.let { RejectedBean(bean, profileId, it.error) }
            }
            Log.i(
                TAG,
                "subscription parsed groupId=$groupId parsed=${beans.size} supported=$supportedCount " +
                    "rejected=${rejected.size} byProtocol=${rejected.groupByProtocol()} " +
                    "byTransport=${rejected.groupByTransport()} byReason=${rejected.groupByReason()}",
            )
        }

        private data class RejectedBean(
            val bean: AbstractBean,
            val profileId: Long,
            val error: BeanSupportError,
        )

        private fun List<RejectedBean>.groupByProtocol(): String =
            groupingBy { it.bean.protocolLabel() }.eachCount().stableDiagnosticString()

        private fun List<RejectedBean>.groupByTransport(): String =
            groupBy {
                val standardBean = it.bean as? StandardV2RayBean
                val raw = standardBean
                    ?.rawTransportType
                    ?.ifBlank { standardBean.type }
                    .orEmpty()
                    .safeTransportLabel()
                val canonical = normalizeSingboxTransport(raw).safeTransportLabel()
                "protocol=${it.bean.protocolLabel()} raw=$raw canonical=$canonical reason=${it.error.name}"
            }
                .toSortedMap()
                .entries
                .joinToString(prefix = "{", postfix = "}") { (key, values) ->
                    "$key count=${values.size} profileIds=${values.map { it.profileId }.filter { it != 0L }.take(3)}"
                }

        private fun List<RejectedBean>.groupByReason(): String =
            groupingBy { it.error.name }.eachCount().stableDiagnosticString()

        private fun Map<String, Int>.stableDiagnosticString(): String =
            entries.sortedBy { it.key }.joinToString(prefix = "{", postfix = "}") { "${it.key}=${it.value}" }

        private fun String.safeTransportLabel(): String =
            trim().lowercase().filter { it.isLetterOrDigit() || it in "-_." }.take(48).ifBlank { "none" }

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

fun isSupportedSubscriptionUrl(value: String): Boolean =
    value.trim().toHttpUrlOrNull()?.host?.isNotBlank() == true

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

private class SubscriptionHttpException(val statusCode: Int) : IOException("Subscription HTTP $statusCode")

private class SubscriptionRefreshStaleException : IOException("Subscription refresh superseded")

private class SubscriptionNoProfilesException : IOException("Subscription contains no supported servers")

private class SubscriptionBodyTooLargeException : IOException("Subscription body too large")

fun isTransientSubscriptionRefreshFailure(error: Throwable): Boolean {
    val causes = generateSequence(error) { it.cause }.toList()
    if (
        causes.any {
            it is SubscriptionNoProfilesException ||
                it is SubscriptionBodyTooLargeException ||
                it is SubscriptionRefreshStaleException
        }
    ) {
        return false
    }
    val http = causes.filterIsInstance<SubscriptionHttpException>().firstOrNull()
    if (http != null) {
        return http.statusCode == 408 ||
            http.statusCode == 425 ||
            http.statusCode == 429 ||
            http.statusCode in 500..599
    }
    if (causes.any { it is SSLHandshakeException || it is SSLPeerUnverifiedException }) return false
    return causes.any { it is SocketTimeoutException || it is UnknownHostException || it is IOException }
}

private fun SubscriptionGroup.subscriptionTlsMode(): String = when {
    isBuiltin -> "system"
    allowInsecureTls -> "insecure"
    else -> "user-ca"
}

private fun ProxyProfile.stableBaseIdentityKey(): String =
    listOf(
        groupId.toString(),
        protocolType.toString(),
        recoveredIdentityBean()
            ?.let { "${it.serverAddress}|${it.serverPort}|${it.stableCredentialKey()}" }
            ?: beanBlob.contentHashCode().toString(),
    ).joinToString("|")

private fun ProxyProfile.stableFullIdentityKey(): String =
    listOf(
        stableBaseIdentityKey(),
        recoveredIdentityBean()
            ?.stableRuntimeKey()
            ?: "",
    ).joinToString("|")

private fun AbstractBean.stableCredentialKey(): String = when (this) {
    is VLESSBean -> "uuid=${uuid.trim()}"
    is VMessBean -> "uuid=${uuid.trim()}"
    is TrojanBean -> "password=${password.trim()}"
    is ShadowsocksBean -> "method=${method.trim()}|password=${password.trim()}"
    is StandardV2RayBean -> "uuid=${uuid.trim()}"
    else -> "blob=${listOf(serverAddress.trim(), serverPort, name.trim()).hashCode()}"
}

private fun ProxyProfile.recoveredIdentityBean(): AbstractBean? =
    PersistedProfileRecovery.recoverIdentity(beanBlob, protocolType)

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
