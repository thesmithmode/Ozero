package ru.ozero.engineurnetwork.auth

import android.content.Context
import com.bringyour.sdk.Api
import com.bringyour.sdk.AuthNetworkClientArgs
import com.bringyour.sdk.AuthNetworkClientCallback
import com.bringyour.sdk.AuthNetworkClientResult
import com.bringyour.sdk.NetworkCreateArgs
import com.bringyour.sdk.NetworkCreateCallback
import com.bringyour.sdk.NetworkCreateResult
import com.bringyour.sdk.NetworkSpace
import com.bringyour.sdk.NetworkSpaceManager
import com.bringyour.sdk.Sdk
import kotlinx.coroutines.suspendCancellableCoroutine
import ru.ozero.enginescore.PersistentLoggers
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume

class RealUrnetworkAuthService(
    private val context: Context,
) : UrnetworkAuthService {

    private val managerRef = AtomicReference<NetworkSpaceManager?>(null)
    private val spaceRef = AtomicReference<NetworkSpace?>(null)

    override suspend fun acquireGuestJwt(): GuestJwtResult {
        val api = ensureApi()
            ?: return GuestJwtResult.Error("URnetwork SDK не инициализирован")

        return suspendCancellableCoroutine { cont ->
            val args = NetworkCreateArgs().apply {
                terms = true
                guestMode = true
            }
            val callback = NetworkCreateCallback { result: NetworkCreateResult?, err: Exception? ->
                when {
                    err != null -> cont.resume(GuestJwtResult.Error(err.message ?: "networkCreate failed"))
                    result == null -> cont.resume(GuestJwtResult.Error("empty response"))
                    result.error != null -> cont.resume(
                        GuestJwtResult.Error(result.error?.message ?: "unknown error"),
                    )
                    result.network != null -> {
                        val jwt = result.network?.byJwt
                        if (jwt.isNullOrBlank()) {
                            cont.resume(GuestJwtResult.Error("server returned empty jwt"))
                        } else {
                            cont.resume(GuestJwtResult.Success(byJwt = jwt))
                        }
                    }
                    else -> cont.resume(GuestJwtResult.Error("unrecognized response"))
                }
            }
            try {
                api.networkCreate(args, callback)
            } catch (t: Throwable) {
                PersistentLoggers.error(TAG, "networkCreate threw: ${t.message}")
                cont.resume(GuestJwtResult.Error(t.message ?: "API call failed"))
            }
        }
    }

    override suspend fun acquireClientJwt(byJwt: String): ClientJwtResult {
        if (byJwt.isBlank()) return ClientJwtResult.Error("byJwt is blank")
        val api = ensureApi()
            ?: return ClientJwtResult.Error("URnetwork SDK не инициализирован")
        runCatching { api.byJwt = byJwt }
            .onFailure { return ClientJwtResult.Error("set api.byJwt failed: ${it.message}") }
        return suspendCancellableCoroutine { cont ->
            val args = AuthNetworkClientArgs().apply {
                description = DEVICE_DESCRIPTION
                deviceSpec = DEVICE_SPEC
            }
            val callback = AuthNetworkClientCallback { result: AuthNetworkClientResult?, err: Exception? ->
                when {
                    err != null -> cont.resume(ClientJwtResult.Error(err.message ?: "authNetworkClient failed"))
                    result == null -> cont.resume(ClientJwtResult.Error("empty response"))
                    result.error != null -> cont.resume(
                        ClientJwtResult.Error(result.error?.message ?: "unknown error"),
                    )
                    else -> {
                        val cjwt = result.byClientJwt
                        if (cjwt.isNullOrBlank()) {
                            cont.resume(ClientJwtResult.Error("server returned empty client jwt"))
                        } else {
                            cont.resume(ClientJwtResult.Success(byClientJwt = cjwt))
                        }
                    }
                }
            }
            try {
                api.authNetworkClient(args, callback)
            } catch (t: Throwable) {
                PersistentLoggers.error(TAG, "authNetworkClient threw: ${t.message}")
                cont.resume(ClientJwtResult.Error(t.message ?: "API call failed"))
            }
        }
    }

    private fun ensureApi(): Api? {
        spaceRef.get()?.let { return it.api }
        return try {
            val storageDir = context.filesDir.resolve(URN_AUTH_STORAGE_DIR).apply { mkdirs() }.absolutePath
            val manager = managerRef.get() ?: Sdk.newNetworkSpaceManager(storageDir).also {
                managerRef.set(it)
            }
            val space = resolveNetworkSpace(manager) ?: run {
                PersistentLoggers.error(TAG, "NetworkSpace null after active/get/import fallback")
                return null
            }
            spaceRef.set(space)
            space.api
        } catch (t: Throwable) {
            PersistentLoggers.error(TAG, "ensureApi threw: ${t.message}\n${t.stackTraceToString()}")
            null
        }
    }

    private fun resolveNetworkSpace(manager: NetworkSpaceManager): NetworkSpace? {
        manager.activeNetworkSpace?.let {
            PersistentLoggers.info(TAG, "using active NetworkSpace")
            return it
        }
        val key = Sdk.newNetworkSpaceKey(DEFAULT_HOST, DEFAULT_ENV)
        manager.getNetworkSpace(key)?.let {
            PersistentLoggers.info(TAG, "using stored NetworkSpace")
            runCatching { manager.setActiveNetworkSpace(it) }
                .onFailure { e -> PersistentLoggers.warn(TAG, "setActiveNetworkSpace(stored) failed: ${e.message}") }
            return it
        }
        val updated = manager.updateNetworkSpace(key) { values ->
            values.envSecret = ""
            values.bundled = true
            values.netExposeServerIps = true
            values.netExposeServerHostNames = true
            values.linkHostName = LINK_HOST_NAME
            values.migrationHostName = MIGRATION_HOST_NAME
            values.store = ""
            values.wallet = WALLET
            values.ssoGoogle = false
        } ?: return null
        manager.setActiveNetworkSpace(updated)
        PersistentLoggers.info(TAG, "updated bundled NetworkSpace host=$DEFAULT_HOST env=$DEFAULT_ENV")
        return updated
    }

    private companion object {
        const val TAG = "RealUrnetworkAuthService"
        const val URN_AUTH_STORAGE_DIR = "urnetwork_auth"
        const val DEFAULT_HOST = "ur.network"
        const val DEFAULT_ENV = "main"
        const val LINK_HOST_NAME = "ur.io"
        const val MIGRATION_HOST_NAME = "bringyour.com"
        const val WALLET = "solana"
        const val DEVICE_DESCRIPTION = "Ozero VPN Android"
        const val DEVICE_SPEC = "android"
    }
}
