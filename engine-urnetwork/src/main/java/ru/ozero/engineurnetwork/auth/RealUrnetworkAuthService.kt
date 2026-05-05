package ru.ozero.engineurnetwork.auth

import android.app.Application
import com.bringyour.sdk.AuthNetworkClientArgs
import com.bringyour.sdk.AuthNetworkClientCallback
import com.bringyour.sdk.AuthNetworkClientResult
import com.bringyour.sdk.NetworkCreateArgs
import com.bringyour.sdk.NetworkCreateCallback
import com.bringyour.sdk.NetworkCreateResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import ru.ozero.engineurnetwork.UrnetworkRuntime
import ru.ozero.enginescore.PersistentLoggers
import kotlin.coroutines.resume

class RealUrnetworkAuthService(
    private val app: Application,
) : UrnetworkAuthService {

    override suspend fun acquireGuestJwt(): GuestJwtResult = withContext(Dispatchers.Main.immediate) {
        val space = runCatching { UrnetworkRuntime.ensure(app) }
            .getOrElse { return@withContext GuestJwtResult.Error("runtime init failed: ${it.message}") }
        val api = space.api ?: return@withContext GuestJwtResult.Error("api null after runtime init")
        suspendCancellableCoroutine { cont ->
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

    override suspend fun acquireClientJwt(byJwt: String): ClientJwtResult = withContext(Dispatchers.Main.immediate) {
        if (byJwt.isBlank()) return@withContext ClientJwtResult.Error("byJwt is blank")
        val space = runCatching { UrnetworkRuntime.ensure(app) }
            .getOrElse { return@withContext ClientJwtResult.Error("runtime init failed: ${it.message}") }
        val api = space.api ?: return@withContext ClientJwtResult.Error("api null")
        runCatching { api.byJwt = byJwt }
            .onFailure { return@withContext ClientJwtResult.Error("set api.byJwt failed: ${it.message}") }
        runCatching { space.asyncLocalState?.localState?.byJwt = byJwt }
            .onFailure { PersistentLoggers.warn(TAG, "set localState.byJwt threw: ${it.message}") }
        suspendCancellableCoroutine { cont ->
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

    private companion object {
        const val TAG = "RealUrnetworkAuthService"
        const val DEVICE_DESCRIPTION = "Ozero VPN Android"
        const val DEVICE_SPEC = "android"
    }
}
