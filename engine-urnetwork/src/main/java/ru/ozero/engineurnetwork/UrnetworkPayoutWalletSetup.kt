package ru.ozero.engineurnetwork

import android.util.Log
import com.bringyour.sdk.AccountWalletsListener
import com.bringyour.sdk.Id
import com.bringyour.sdk.Sub
import com.bringyour.sdk.WalletViewController
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import ru.ozero.enginescore.PersistentLoggers

internal class UrnetworkPayoutWalletSetup {

    suspend fun configure(walletVc: WalletViewController, walletAddress: String) {
        if (walletAddress.isBlank()) {
            Log.i(TAG, "setupPayoutWallet skipped — empty walletAddress")
            return
        }
        runCatching {
            val existing = awaitWalletsChanged(walletVc, WALLET_FETCH_TIMEOUT_MS) {
                walletVc.fetchAccountWallets()
            }
            var walletId: Id? = findWalletIdByAddress(walletVc, walletAddress)
            if (walletId == null && existing != null) {
                val added = awaitWalletsChanged(walletVc, WALLET_ADD_TIMEOUT_MS) {
                    walletVc.addExternalWallet(walletAddress, WALLET_BLOCKCHAIN_SOLANA)
                }
                if (added != null) {
                    walletId = findWalletIdByAddress(walletVc, walletAddress)
                }
            }
            if (walletId != null) {
                walletVc.updatePayoutWallet(walletId)
                Log.i(TAG, "payout wallet set")
            } else {
                PersistentLoggers.warn(TAG, "payout wallet setup: walletId not found")
            }
        }.onFailure {
            PersistentLoggers.warn(TAG, "setupPayoutWallet threw: ${it.message}")
        }
    }

    private suspend fun awaitWalletsChanged(
        walletVc: WalletViewController,
        timeoutMs: Long,
        trigger: () -> Unit,
    ): Unit? {
        val deferred = CompletableDeferred<Unit>()
        var sub: Sub? = null
        sub = walletVc.addAccountWalletsListener(
            AccountWalletsListener {
                runCatching { sub?.close() }
                deferred.complete(Unit)
            },
        )
        return withTimeoutOrNull(timeoutMs) {
            runCatching { trigger() }
                .onFailure { PersistentLoggers.warn(TAG, "wallets trigger threw: ${it.message}") }
            deferred.await()
        }.also {
            if (it == null) runCatching { sub?.close() }
        }
    }

    private fun findWalletIdByAddress(walletVc: WalletViewController, address: String): Id? {
        val list = runCatching { walletVc.wallets }.getOrNull() ?: return null
        val count = runCatching { list.len() }.getOrDefault(0L)
        for (i in 0 until count) {
            val w = runCatching { list.get(i) }.getOrNull() ?: continue
            val addr = runCatching { w.walletAddress }.getOrNull()
            if (addr == address) {
                return runCatching { w.walletId }.getOrNull()
            }
        }
        return null
    }

    private companion object {
        const val TAG = "UrnetworkPayoutWallet"
        const val WALLET_FETCH_TIMEOUT_MS = 5_000L
        const val WALLET_ADD_TIMEOUT_MS = 10_000L
        const val WALLET_BLOCKCHAIN_SOLANA = "solana"
    }
}
