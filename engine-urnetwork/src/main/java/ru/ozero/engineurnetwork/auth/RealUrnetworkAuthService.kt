package ru.ozero.engineurnetwork.auth

import android.content.Context
import com.bringyour.sdk.Api
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

    private fun ensureApi(): Api? {
        spaceRef.get()?.let { return it.api }
        return try {
            val storageDir = context.filesDir.resolve(URN_AUTH_STORAGE_DIR).apply { mkdirs() }.absolutePath
            val manager = managerRef.get() ?: Sdk.newNetworkSpaceManager(storageDir).also {
                managerRef.set(it)
            }
            val space = manager.activeNetworkSpace ?: run {
                val key = Sdk.newNetworkSpaceKey(DEFAULT_HOST, DEFAULT_ENV)
                val existing = manager.getNetworkSpace(key)
                if (existing != null) {
                    existing
                } else {
                    val imported = manager.importNetworkSpaceFromJson(
                        """{"host_name":"$DEFAULT_HOST","env_name":"$DEFAULT_ENV"}"""
                    )
                    manager.setActiveNetworkSpace(imported)
                    imported
                }
            }
            spaceRef.set(space)
            space.api
        } catch (t: Throwable) {
            PersistentLoggers.error(TAG, "ensureApi threw: ${t.message}")
            null
        }
    }

    private companion object {
        const val TAG = "RealUrnetworkAuthService"
        const val URN_AUTH_STORAGE_DIR = "urnetwork_auth"
        const val DEFAULT_HOST = "ur.network"
        const val DEFAULT_ENV = "prod"
    }
}
