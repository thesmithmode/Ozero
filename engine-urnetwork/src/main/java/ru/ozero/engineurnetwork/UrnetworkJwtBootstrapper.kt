package ru.ozero.engineurnetwork

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ru.ozero.engineurnetwork.auth.ClientJwtResult
import ru.ozero.engineurnetwork.auth.DeviceWalletJwtResult
import ru.ozero.engineurnetwork.auth.GuestJwtResult
import ru.ozero.engineurnetwork.auth.UrnetworkAuthService
import ru.ozero.engineurnetwork.auth.UrnetworkDeviceIdentity
import ru.ozero.enginescore.PersistentLoggers

interface UrnetworkJwtBootstrapper {
    suspend fun ensureClientJwt(): Result

    sealed class Result {
        object AlreadyPresent : Result()
        object Acquired : Result()
        data class Failed(val reason: String) : Result()
    }
}

class RealUrnetworkJwtBootstrapper(
    private val configStore: UrnetworkConfigStore,
    private val authService: UrnetworkAuthService,
    private val deviceIdentity: UrnetworkDeviceIdentity?,
    private val networkNameGenerator: () -> String = { defaultNetworkName() },
) : UrnetworkJwtBootstrapper {

    private val mutex = Mutex()

    override suspend fun ensureClientJwt(): UrnetworkJwtBootstrapper.Result = mutex.withLock {
        val preCjwt = configStore.byClientJwt().first()
        val preByJwt = configStore.byJwt().first()
        val prePubkey = configStore.devicePubkey().first()
        val migrationPending = preByJwt != null && prePubkey.isNullOrBlank() && deviceIdentity != null
        if (preCjwt != null && !migrationPending) {
            return@withLock UrnetworkJwtBootstrapper.Result.AlreadyPresent
        }

        val byJwt = ensureGuestJwt()
            ?: return@withLock UrnetworkJwtBootstrapper.Result.Failed(
                "URnetwork guest jwt acquire failed — нет интернета или сервер недоступен",
            )

        val postGuestCheck = configStore.byClientJwt().first()
        if (postGuestCheck != null) return@withLock UrnetworkJwtBootstrapper.Result.AlreadyPresent

        when (val r = authService.acquireClientJwt(byJwt)) {
            is ClientJwtResult.Success -> {
                configStore.setByClientJwt(r.byClientJwt)
                PersistentLoggers.info(TAG, "client jwt acquired and persisted")
                UrnetworkJwtBootstrapper.Result.Acquired
            }
            is ClientJwtResult.Error -> {
                PersistentLoggers.error(TAG, "acquireClientJwt failed: ${r.message}")
                UrnetworkJwtBootstrapper.Result.Failed(
                    "URnetwork client jwt acquire failed — нет интернета или сервер недоступен",
                )
            }
        }
    }

    private suspend fun ensureGuestJwt(): String? {
        val existing = configStore.byJwt().first()
        val pubkey = configStore.devicePubkey().first()
        if (existing != null && !pubkey.isNullOrBlank()) return existing
        val deviceJwt = tryAcquireDeviceJwt(legacyMigration = existing != null)
        if (deviceJwt != null) return deviceJwt
        if (existing != null) {
            PersistentLoggers.info(TAG, "device walletAuth unavailable — keeping legacy guest byJwt")
            return existing
        }
        PersistentLoggers.info(TAG, "device walletAuth unavailable — fallback to guest network")
        return when (val r = authService.acquireGuestJwt()) {
            is GuestJwtResult.Success -> {
                configStore.setByJwt(r.byJwt)
                PersistentLoggers.info(TAG, "guest jwt acquired and persisted")
                r.byJwt
            }
            is GuestJwtResult.Error -> {
                PersistentLoggers.error(TAG, "acquireGuestJwt failed: ${r.message}")
                null
            }
        }
    }

    private suspend fun tryAcquireDeviceJwt(legacyMigration: Boolean): String? {
        val identity = deviceIdentity ?: return null
        val storedName = configStore.deviceNetworkName().first()
        val networkName = storedName?.takeIf { it.isNotBlank() } ?: networkNameGenerator()
        PersistentLoggers.info(
            TAG,
            if (legacyMigration) {
                "device walletAuth — migrating legacy guest byJwt to per-device keypair"
            } else {
                "device walletAuth — acquiring jwt via per-device keypair"
            },
        )
        return when (val r = authService.acquireDeviceWalletJwt(identity, networkName)) {
            is DeviceWalletJwtResult.Success -> {
                val pubkey = runCatching { identity.pubkeyBase58() }.getOrNull()
                configStore.update { cfg ->
                    cfg.copy(
                        byJwt = r.byJwt,
                        byClientJwt = if (legacyMigration) null else cfg.byClientJwt,
                        devicePubkey = pubkey ?: cfg.devicePubkey,
                        deviceNetworkName = networkName,
                    )
                }
                PersistentLoggers.info(
                    TAG,
                    "device walletAuth jwt acquired isNew=${r.isNewNetwork} " +
                        "migration=$legacyMigration " +
                        "pubkey=${pubkey?.take(PUBKEY_LOG_PREFIX_LEN) ?: "?"}…",
                )
                r.byJwt
            }
            is DeviceWalletJwtResult.Error -> {
                PersistentLoggers.warn(TAG, "device walletAuth failed: ${r.message}")
                null
            }
        }
    }

    private companion object {
        const val TAG = "UrnJwtBootstrapper"
        const val PUBKEY_LOG_PREFIX_LEN = 8
        const val NETWORK_NAME_RANDOM_BYTES = 8

        fun defaultNetworkName(): String {
            val rnd = java.security.SecureRandom()
            val bytes = ByteArray(NETWORK_NAME_RANDOM_BYTES).also { rnd.nextBytes(it) }
            val hex = bytes.joinToString(separator = "") { "%02x".format(it.toInt() and 0xff) }
            return "n$hex"
        }
    }
}
