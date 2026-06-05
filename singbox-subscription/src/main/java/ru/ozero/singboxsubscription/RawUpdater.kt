package ru.ozero.singboxsubscription

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.net.ssl.SSLHandshakeException
import ru.ozero.singboxfmt.AbstractBean
import ru.ozero.singboxfmt.KryoSerializer
import ru.ozero.singboxfmt.ShadowsocksBean
import ru.ozero.singboxfmt.TrojanBean
import ru.ozero.singboxfmt.VMessBean
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
) {
    suspend fun refresh(group: SubscriptionGroup): Result<Int> = withContext(Dispatchers.IO) {
        runCatching<Int> {
            val request = Request.Builder()
                .url(group.subscriptionUrl)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/plain, application/json, application/yaml, text/yaml, */*")
                .build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    error("Subscription HTTP ${response.code}")
                }
                val body = response.body?.string() ?: ""
                val subInfo = SubscriptionInfoParser.parse(response.header("Subscription-Userinfo"))

                val beans = Base64BundleParser.parse(body)
                    .ifEmpty { RawShareLinksParser.parse(body) }

                val profiles = beans.mapIndexed { idx, bean ->
                    ProxyProfile(
                        groupId = group.id,
                        name = bean.name.ifBlank { "Server ${idx + 1}" },
                        beanBlob = KryoSerializer.serialize(bean),
                        protocolType = protocolTypeOf(bean),
                        userOrder = idx,
                    )
                }
                val existingProfiles = profileDao.getByGroupId(group.id)
                val existingByStableIdentity = existingProfiles
                    .groupBy { it.stableIdentityKey() }
                val profilesWithStableIds = profiles.map { profile ->
                    val stableKey = profile.stableIdentityKey()
                    val matched = existingByStableIdentity[stableKey].orEmpty().firstOrNull()
                    if (matched != null) {
                        profile.copy(id = matched.id)
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

    companion object {
        private const val TAG = "RawUpdater"
        const val PROTOCOL_VLESS = 0
        const val PROTOCOL_VMESS = 1
        const val PROTOCOL_TROJAN = 2
        const val PROTOCOL_SHADOWSOCKS = 3

        private const val USER_AGENT = "Ozero/1 sing-box-subscription"

        private fun normalizeError(e: Throwable): Throwable = when {
            e is SSLHandshakeException && e.message?.contains("Chain validation failed", ignoreCase = true) == true ->
                SSLHandshakeException("Subscription TLS certificate chain validation failed")
            else -> e
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

private fun ProxyProfile.stableIdentityKey(): String =
    listOf(
        groupId.toString(),
        protocolType.toString(),
        name.trim(),
        runCatching { KryoSerializer.deserialize<AbstractBean>(beanBlob) }
            .getOrNull()
            ?.let { "${it.serverAddress}|${it.serverPort}" }
            ?: beanBlob.contentHashCode().toString(),
    ).joinToString("|")
