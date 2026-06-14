package ru.ozero.engineurnetwork.auth

import android.app.Application
import android.util.Base64
import com.bringyour.sdk.Api
import com.bringyour.sdk.AuthLoginArgs
import com.bringyour.sdk.AuthLoginCallback
import com.bringyour.sdk.AuthLoginResult
import com.bringyour.sdk.AuthNetworkClientArgs
import com.bringyour.sdk.AuthNetworkClientCallback
import com.bringyour.sdk.AuthNetworkClientResult
import com.bringyour.sdk.NetworkCreateArgs
import com.bringyour.sdk.NetworkCreateCallback
import com.bringyour.sdk.NetworkCreateResult
import com.bringyour.sdk.WalletAuthArgs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import ru.ozero.engineurnetwork.UrnetworkDefaults
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
        suspendCancellableCoroutine<GuestJwtResult> { cont ->
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
        suspendCancellableCoroutine<ClientJwtResult> { cont ->
            val args = AuthNetworkClientArgs().apply {
                description = UrnetworkDefaults.DEVICE_DESCRIPTION
                deviceSpec = UrnetworkDefaults.DEVICE_SPEC
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

    override suspend fun acquireDeviceWalletJwt(
        identity: UrnetworkDeviceIdentity,
        networkName: String,
    ): DeviceWalletJwtResult = withContext(Dispatchers.Main.immediate) {
        val space = runCatching { UrnetworkRuntime.ensure(app) }
            .getOrElse {
                return@withContext DeviceWalletJwtResult.Error("runtime init failed: ${it.message}")
            }
        val api = space.api
            ?: return@withContext DeviceWalletJwtResult.Error("api null after runtime init")
        val walletAuth = UrnetworkWalletAuthMapper.buildWalletAuth(identity)
            ?: return@withContext DeviceWalletJwtResult.Error("identity sign failed")
        when (val r = authLoginWithWallet(api, walletAuth)) {
            is LoginOutcome.Existing -> DeviceWalletJwtResult.Success(r.byJwt, isNewNetwork = false)
            is LoginOutcome.NeedCreate -> networkCreateWithWallet(api, walletAuth, networkName)
            is LoginOutcome.Error -> DeviceWalletJwtResult.Error("authLogin: ${r.message}")
        }
    }

    private suspend fun authLoginWithWallet(api: Api, walletAuth: WalletAuthArgs): LoginOutcome =
        suspendCancellableCoroutine { cont ->
            val args = AuthLoginArgs().apply { this.walletAuth = walletAuth }
            val callback = AuthLoginCallback { result: AuthLoginResult?, err: Exception? ->
                cont.resume(UrnetworkWalletAuthMapper.mapLoginOutcome(result, err))
            }
            try {
                api.authLogin(args, callback)
            } catch (t: Throwable) {
                PersistentLoggers.error(TAG, "authLogin threw: ${t.message}")
                cont.resume(LoginOutcome.Error(t.message ?: "API call failed"))
            }
        }

    private suspend fun networkCreateWithWallet(
        api: Api,
        walletAuth: WalletAuthArgs,
        networkName: String,
    ): DeviceWalletJwtResult = suspendCancellableCoroutine { cont ->
        val args = NetworkCreateArgs().apply {
            userName = ""
            this.networkName = networkName
            terms = true
            this.walletAuth = walletAuth
        }
        val callback = NetworkCreateCallback { result: NetworkCreateResult?, err: Exception? ->
            cont.resume(UrnetworkWalletAuthMapper.mapCreateOutcome(result, err))
        }
        try {
            api.networkCreate(args, callback)
        } catch (t: Throwable) {
            PersistentLoggers.error(TAG, "networkCreate(wallet) threw: ${t.message}")
            cont.resume(DeviceWalletJwtResult.Error(t.message ?: "API call failed"))
        }
    }

    private companion object {
        const val TAG = "RealUrnetworkAuthService"
    }
}

internal object UrnetworkWalletAuthMapper {
    suspend fun buildWalletAuth(
        identity: UrnetworkDeviceIdentity,
        encodeSignature: (ByteArray) -> String = { raw -> Base64.encodeToString(raw, Base64.NO_WRAP) },
    ): WalletAuthArgs? {
        val pubkey = runCatching { identity.pubkeyBase58() }
            .getOrElse {
                PersistentLoggers.warn(TAG, "identity.pubkeyBase58 threw: ${it.message}")
                return null
            }
        val message = WALLET_MESSAGE_PREFIX + pubkey
        val signature = runCatching {
            val raw = identity.sign(message.toByteArray(Charsets.UTF_8))
            encodeSignature(raw)
        }.getOrElse {
            PersistentLoggers.warn(TAG, "identity.sign threw: ${it.message}")
            return null
        }
        return WalletAuthArgs().apply {
            publicKey = pubkey
            this.message = message
            this.signature = signature
            blockchain = WALLET_BLOCKCHAIN_SOLANA
        }
    }

    fun mapLoginOutcome(result: AuthLoginResult?, err: Exception?): LoginOutcome = when {
        err != null -> LoginOutcome.Error(err.message ?: "authLogin failed")
        result == null -> LoginOutcome.Error("empty authLogin response")
        result.error != null -> LoginOutcome.Error(result.error?.message ?: "authLogin error")
        else -> {
            val byJwt = runCatching { result.network?.byJwt }.getOrNull()
            val echoed = runCatching { result.walletAuth }.getOrNull()
            when {
                !byJwt.isNullOrBlank() -> LoginOutcome.Existing(byJwt)
                echoed != null -> LoginOutcome.NeedCreate
                else -> LoginOutcome.Error("unrecognized authLogin response")
            }
        }
    }

    fun mapCreateOutcome(result: NetworkCreateResult?, err: Exception?): DeviceWalletJwtResult =
        when {
            err != null -> DeviceWalletJwtResult.Error(err.message ?: "networkCreate failed")
            result == null -> DeviceWalletJwtResult.Error("empty networkCreate response")
            result.error != null ->
                DeviceWalletJwtResult.Error(result.error?.message ?: "networkCreate error")
            else -> {
                val jwt = runCatching { result.network?.byJwt }.getOrNull()
                if (jwt.isNullOrBlank()) {
                    DeviceWalletJwtResult.Error("networkCreate returned empty jwt")
                } else {
                    DeviceWalletJwtResult.Success(byJwt = jwt, isNewNetwork = true)
                }
            }
        }

    const val WALLET_BLOCKCHAIN_SOLANA = "solana"
    const val WALLET_MESSAGE_PREFIX = "ozero-auth-v1:"
    private const val TAG = "RealUrnetworkAuthService"
}

internal sealed class LoginOutcome {
    data class Existing(val byJwt: String) : LoginOutcome()
    object NeedCreate : LoginOutcome()
    data class Error(val message: String) : LoginOutcome()
}
