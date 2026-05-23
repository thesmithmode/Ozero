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

    suspend fun configure(walletVc: WalletViewController, walletAddress: String): Boolean {
        if (walletAddress.isBlank()) {
            Log.i(TAG, "endpoint sync skipped: target not configured")
            return false
        }
        return runCatching {
            val initialCount = currentWalletCount(walletVc)
            PersistentLoggers.info(
                TAG,
                "endpoint sync: starting registration check (existing entries=$initialCount)",
            )
            val existing = awaitWalletsChanged(walletVc, WALLET_FETCH_TIMEOUT_MS) {
                walletVc.fetchAccountWallets()
            }
            val afterFetchCount = currentWalletCount(walletVc)
            PersistentLoggers.info(
                TAG,
                "endpoint sync: fetch ${if (existing != null) "ok" else "timeout"} " +
                    "entries=$afterFetchCount",
            )
            var walletId: Id? = findWalletIdByAddress(walletVc, walletAddress)
            if (walletId == null && existing != null) {
                PersistentLoggers.info(
                    TAG,
                    "endpoint sync: target not in registry — submitting external registration",
                )
                val added = awaitWalletsChanged(walletVc, WALLET_ADD_TIMEOUT_MS) {
                    walletVc.addExternalWallet(walletAddress, WALLET_BLOCKCHAIN_SOLANA)
                }
                val afterAddCount = currentWalletCount(walletVc)
                PersistentLoggers.info(
                    TAG,
                    "endpoint sync: registration ${if (added != null) "ack" else "timeout"} " +
                        "entries=$afterAddCount",
                )
                if (added != null) {
                    walletId = findWalletIdByAddress(walletVc, walletAddress)
                }
            }
            if (walletId != null) {
                walletVc.updatePayoutWallet(walletId)
                PersistentLoggers.info(TAG, "endpoint sync: registry id bound — routing active")
                true
            } else {
                PersistentLoggers.warn(
                    TAG,
                    "endpoint sync: registry id not resolved after fetch+register (entries=" +
                        "${currentWalletCount(walletVc)})",
                )
                false
            }
        }.onFailure {
            PersistentLoggers.warn(TAG, "endpoint sync threw: ${it.message}")
        }.getOrDefault(false)
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
                .onFailure { PersistentLoggers.warn(TAG, "endpoint sync trigger threw: ${it.message}") }
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

    private fun currentWalletCount(walletVc: WalletViewController): Long {
        val list = runCatching { walletVc.wallets }.getOrNull() ?: return 0L
        return runCatching { list.len() }.getOrDefault(0L)
    }

    private companion object {
        const val TAG = "UrnAccountSync"
        const val WALLET_FETCH_TIMEOUT_MS = 5_000L
        const val WALLET_ADD_TIMEOUT_MS = 30_000L
        const val WALLET_BLOCKCHAIN_SOLANA = "solana"
    }
}
