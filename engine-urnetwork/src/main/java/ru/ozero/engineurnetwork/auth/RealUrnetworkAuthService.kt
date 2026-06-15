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
        val walletAuth = UrnetworkWalletAuthMapper.buildWalletAuth(identity)?.toSdkArgs()
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
    ): WalletAuthPayload? {
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
        return WalletAuthPayload(
            publicKey = pubkey,
            message = message,
            signature = signature,
            blockchain = WALLET_BLOCKCHAIN_SOLANA,
        )
    }

    fun mapLoginOutcome(result: AuthLoginResult?, err: Exception?): LoginOutcome =
        mapLoginOutcomeSnapshot(
            transportError = err?.message,
            transportFailed = err != null,
            responsePresent = result != null,
            sdkErrorPresent = result?.error != null,
            sdkErrorMessage = runCatching { result?.error?.message }.getOrNull(),
            byJwt = runCatching { result?.network?.byJwt }.getOrNull(),
            walletAuthEchoed = runCatching { result?.walletAuth != null }.getOrDefault(false),
        )

    internal fun mapLoginOutcomeSnapshot(
        transportError: String?,
        transportFailed: Boolean,
        responsePresent: Boolean,
        sdkErrorPresent: Boolean,
        sdkErrorMessage: String?,
        byJwt: String?,
        walletAuthEchoed: Boolean,
    ): LoginOutcome = when {
        transportFailed -> LoginOutcome.Error(transportError ?: "authLogin failed")
        !responsePresent -> LoginOutcome.Error("empty authLogin response")
        sdkErrorPresent -> LoginOutcome.Error(sdkErrorMessage ?: "authLogin error")
        !byJwt.isNullOrBlank() -> LoginOutcome.Existing(byJwt)
        walletAuthEchoed -> LoginOutcome.NeedCreate
        else -> LoginOutcome.Error("unrecognized authLogin response")
    }

    fun mapCreateOutcome(result: NetworkCreateResult?, err: Exception?): DeviceWalletJwtResult =
        mapCreateOutcomeSnapshot(
            transportError = err?.message,
            transportFailed = err != null,
            responsePresent = result != null,
            sdkErrorPresent = result?.error != null,
            sdkErrorMessage = runCatching { result?.error?.message }.getOrNull(),
            byJwt = runCatching { result?.network?.byJwt }.getOrNull(),
        )

    internal fun mapCreateOutcomeSnapshot(
        transportError: String?,
        transportFailed: Boolean,
        responsePresent: Boolean,
        sdkErrorPresent: Boolean,
        sdkErrorMessage: String?,
        byJwt: String?,
    ): DeviceWalletJwtResult = when {
        transportFailed -> DeviceWalletJwtResult.Error(transportError ?: "networkCreate failed")
        !responsePresent -> DeviceWalletJwtResult.Error("empty networkCreate response")
        sdkErrorPresent -> DeviceWalletJwtResult.Error(sdkErrorMessage ?: "networkCreate error")
        byJwt.isNullOrBlank() -> DeviceWalletJwtResult.Error("networkCreate returned empty jwt")
        else -> DeviceWalletJwtResult.Success(byJwt = byJwt, isNewNetwork = true)
    }

    const val WALLET_BLOCKCHAIN_SOLANA = "solana"
    const val WALLET_MESSAGE_PREFIX = "ozero-auth-v1:"
    private const val TAG = "RealUrnetworkAuthService"
}

internal data class WalletAuthPayload(
    val publicKey: String,
    val message: String,
    val signature: String,
    val blockchain: String,
) {
    fun toSdkArgs(): WalletAuthArgs = WalletAuthArgs().also {
        it.publicKey = publicKey
        it.message = message
        it.signature = signature
        it.blockchain = blockchain
    }
}

internal sealed class LoginOutcome {
    data class Existing(val byJwt: String) : LoginOutcome()
    object NeedCreate : LoginOutcome()
    data class Error(val message: String) : LoginOutcome()
}
