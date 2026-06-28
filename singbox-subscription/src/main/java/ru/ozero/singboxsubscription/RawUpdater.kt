package ru.ozero.singboxsubscription

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.security.cert.CertPathValidatorException
import java.security.cert.CertificateException
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException
import ru.ozero.singboxfmt.AbstractBean
import ru.ozero.singboxfmt.KryoSerializer
import ru.ozero.singboxfmt.ShadowsocksBean
import ru.ozero.singboxfmt.StandardV2RayBean
import ru.ozero.singboxfmt.TrojanBean
import ru.ozero.singboxfmt.VMessBean
import ru.ozero.singboxfmt.VLESSBean
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
) {
    suspend fun refresh(group: SubscriptionGroup): Result<Int> = withContext(Dispatchers.IO) {
        runCatching<Int> {
            val request = Request.Builder()
                .url(group.subscriptionUrl)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/plain, application/json, application/yaml, text/yaml, */*")
                .build()
            httpClientFor(group).newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    error("Subscription HTTP ${response.code}")
                }
                val body = response.body?.readUtf8Limited(MAX_SUBSCRIPTION_BYTES) ?: ""
                val subInfo = SubscriptionInfoParser.parse(response.header("Subscription-Userinfo"))

                val beans = Base64BundleParser.parse(body)
                    .ifEmpty { RawShareLinksParser.parse(body) }

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

                val usedBytes = subInfo?.let { it.uploadBytes + it.downloadBytes } ?: group.bytesUsed
                val remainingBytes = subInfo?.let {
                    maxOf(0L, it.totalBytes - it.uploadBytes - it.downloadBytes)
                } ?: group.bytesRemaining
                groupDao.update(
                    group.copy(
                        lastUpdated = System.currentTimeMillis(),
                        bytesUsed = usedBytes,
                        bytesRemaining = remainingBytes,
                        expiryDate = subInfo?.expiryTimestamp ?: group.expiryDate,
                    ),
                )

                Log.i(TAG, "refresh ok groupId=${group.id} servers=${profilesWithStableIds.size}")
                profilesWithStableIds.size
            }
        }.recoverCatching { e ->
            throw normalizeError(e)
        }.onFailure { e ->
            Log.w(TAG, "refresh failed groupId=${group.id}: ${e.message}")
        }
    }

    private fun httpClientFor(group: SubscriptionGroup): OkHttpClient =
        if (group.isBuiltin) okHttpClient else userCaOkHttpClient

    companion object {
        private const val TAG = "RawUpdater"
        const val PROTOCOL_VLESS = 0
        const val PROTOCOL_VMESS = 1
        const val PROTOCOL_TROJAN = 2
        const val PROTOCOL_SHADOWSOCKS = 3

        private const val USER_AGENT = "Ozero/1 sing-box-subscription"
        private const val MAX_PROFILES_PER_GROUP = 2_000
        private const val MAX_SUBSCRIPTION_BYTES = 4L * 1024 * 1024

        private fun normalizeError(e: Throwable): Throwable = when {
            e.isSubscriptionCertificateFailure() ->
                SSLHandshakeException("Subscription TLS certificate validation failed").initCause(e)
            else -> e
        }

        private fun Throwable.isSubscriptionCertificateFailure(): Boolean {
            if (this !is SSLHandshakeException && this !is SSLPeerUnverifiedException) return false
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
        throw IOException("Subscription body too large")
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
                throw IOException("Subscription body too large")
            }
            out.write(buffer, 0, read)
        }
    }
    return out.toString(charset()?.name() ?: Charsets.UTF_8.name())
}

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
